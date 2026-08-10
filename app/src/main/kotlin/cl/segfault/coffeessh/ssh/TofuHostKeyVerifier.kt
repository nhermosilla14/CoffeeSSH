package cl.segfault.coffeessh.ssh

import cl.segfault.coffeessh.data.db.KnownHostDao
import cl.segfault.coffeessh.data.db.KnownHostEntity
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier

/**
 * Trust-On-First-Use host key verification (see PLAN.md section 5).
 *
 * Important: [verify] runs synchronously on sshj's transport thread *during key
 * exchange*, which is itself subject to sshj's own kex-completion timeout
 * (`KeyExchanger.waitForDone()` / `Promise.retrieve(transport.getTimeoutMs(), ...)`).
 * Blocking here on a UI decision would race that internal timeout against however
 * long a human takes to tap a button - not a fair race at any timeout value. So this
 * verifier always returns `true` to let kex complete quickly, merely *recording*
 * whether the key was already known, unknown, or changed. [SshSession] inspects
 * [lastResult] once [net.schmizz.sshj.SSHClient.connect] returns (safely outside any
 * sshj-internal timeout) and only then shows a blocking confirmation - disconnecting
 * immediately, before any authentication, if the user rejects it.
 */
class TofuHostKeyVerifier(private val knownHostDao: KnownHostDao) : HostKeyVerifier {

    sealed interface Result {
        data object Trusted : Result
        data class NeedsDecision(
            val host: String,
            val port: Int,
            val keyType: String,
            val fingerprint: String,
            val isChanged: Boolean,
        ) : Result
    }

    @Volatile
    var lastResult: Result? = null
        private set

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val keyType = sshPublicKeyType(key)
        val fingerprint = fingerprintOf(key)
        val existing = runBlocking {
            knownHostDao.findFor(hostname, port).firstOrNull { it.keyType == keyType }
        }
        lastResult = if (existing != null && existing.fingerprint == fingerprint) {
            Result.Trusted
        } else {
            Result.NeedsDecision(hostname, port, keyType, fingerprint, isChanged = existing != null)
        }
        return true
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> = runBlocking {
        knownHostDao.findFor(hostname, port).map { it.keyType }.toMutableList()
    }

    suspend fun persist(result: Result.NeedsDecision) {
        knownHostDao.upsert(
            KnownHostEntity(host = result.host, port = result.port, keyType = result.keyType, fingerprint = result.fingerprint),
        )
    }

    companion object {
        /** OpenSSH-style `SHA256:base64(sha256(sshWireFormatBlob))`, no padding. */
        fun fingerprintOf(key: PublicKey): String {
            val blob = sshPublicKeyBlob(key)
            val digest = MessageDigest.getInstance("SHA-256").digest(blob)
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
        }
    }
}
