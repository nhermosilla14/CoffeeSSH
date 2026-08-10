package cl.segfault.coffeessh.ssh

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface CopyKeyResult {
    data object Success : CopyKeyResult

    /** The key blob was already present in `authorized_keys`; nothing was appended. */
    data object AlreadyPresent : CopyKeyResult

    data class Failed(val message: String) : CopyKeyResult
}

/**
 * ssh-copy-id semantics: connect with the identity's existing credentials and append
 * [publicKey] to `~/.ssh/authorized_keys` — but only if the key blob isn't already
 * there (dedup first, per PLAN.md section 7).
 *
 * Host key handling reuses the app's TOFU model instead of blindly accepting any key:
 * [TofuHostKeyVerifier] records whether the presented key is trusted/unknown/changed
 * and, for the latter two, [confirmHostKey] is consulted (the UI shows the same
 * accept/reject prompt as a terminal session; rejecting aborts before authentication).
 */
object SshCopyKeyExecutor {

    suspend fun execute(
        host: String,
        port: Int,
        username: String,
        password: String?,
        privateKeyPem: String?,
        publicKey: String,
        hostKeyVerifier: TofuHostKeyVerifier,
        onProgress: (String) -> Unit,
        confirmHostKey: suspend (TofuHostKeyVerifier.Result.NeedsDecision) -> Boolean,
    ): CopyKeyResult {
        return withContext(Dispatchers.IO) {
            var client: SSHClient? = null
            try {
                onProgress("Connecting to $host:$port...")
                val c = SSHClient(coffeeSshConfig())
                client = c
                c.connectTimeout = 10_000
                c.timeout = 15_000
                c.addHostKeyVerifier(hostKeyVerifier)
                c.connect(host, port)

                when (val result = hostKeyVerifier.lastResult) {
                    is TofuHostKeyVerifier.Result.NeedsDecision -> {
                        if (!confirmHostKey(result)) {
                            return@withContext CopyKeyResult.Failed("Host key rejected")
                        }
                        hostKeyVerifier.persist(result)
                    }
                    else -> {} // trusted, or (unexpectedly) no verifier result: proceed
                }

                onProgress("Authenticating...")
                authenticate(c, username, password, privateKeyPem)

                // Each SSH session channel supports a single exec; open one per command.
                fun execOnce(command: String): String {
                    val s = c.startSession()
                    try {
                        val cmd = s.exec(command)
                        val output = cmd.inputStream.bufferedReader().readText()
                        cmd.join(10, TimeUnit.SECONDS)
                        return output
                    } finally {
                        s.close()
                    }
                }

                onProgress("Creating ~/.ssh directory...")
                execOnce("mkdir -p ~/.ssh && chmod 700 ~/.ssh")

                onProgress("Checking for existing key...")
                val existingKeys = execOnce("cat ~/.ssh/authorized_keys 2>/dev/null || true")

                val keyBlob = keyBlobOf(publicKey)
                    ?: return@withContext CopyKeyResult.Failed("Malformed public key")
                val alreadyPresent = existingKeys.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .any { line -> line.split(Regex("\\s+")).getOrNull(1) == keyBlob }

                if (alreadyPresent) {
                    CopyKeyResult.AlreadyPresent
                } else {
                    onProgress("Appending public key to authorized_keys...")
                    val appendSession = c.startSession()
                    try {
                        val append = appendSession.exec("cat >> ~/.ssh/authorized_keys")
                        append.outputStream.use { os ->
                            os.write(publicKey.trim().toByteArray())
                            os.write("\n".toByteArray())
                            os.flush()
                        }
                        append.join(10, TimeUnit.SECONDS)
                    } finally {
                        appendSession.close()
                    }

                    onProgress("Setting permissions...")
                    execOnce("chmod 600 ~/.ssh/authorized_keys")

                    CopyKeyResult.Success
                }
            } catch (e: Exception) {
                CopyKeyResult.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                runCatching { client?.disconnect() }
            }
        }
    }

    private fun authenticate(client: SSHClient, username: String, password: String?, privateKeyPem: String?) {
        val methods = mutableListOf<net.schmizz.sshj.userauth.method.AuthMethod>()
        if (!privateKeyPem.isNullOrBlank()) {
            methods += AuthPublickey(loadCoffeeSshKeys(client, privateKeyPem))
        }
        if (!password.isNullOrBlank()) {
            val finder = object : PasswordFinder {
                override fun reqPassword(resource: Resource<*>): CharArray = password.toCharArray()
                override fun shouldRetry(resource: Resource<*>): Boolean = false
            }
            methods += AuthPassword(finder)
            methods += AuthKeyboardInteractive(PasswordResponseProvider(finder))
        }
        check(methods.isNotEmpty()) { "No credentials provided" }
        client.auth(username, methods)
    }

    /** Extracts the base64 blob (second field) from an OpenSSH public key line. */
    internal fun keyBlobOf(publicKeyLine: String): String? =
        publicKeyLine.trim().split(Regex("\\s+")).getOrNull(1)?.takeIf { it.isNotEmpty() }
}
