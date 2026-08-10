package cl.segfault.coffeessh.ssh

import cl.segfault.coffeessh.data.db.KnownHostDao
import cl.segfault.coffeessh.data.db.KnownHostEntity
import java.net.InetSocketAddress
import java.net.Socket
import java.security.Security
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * End-to-end proof of the M4 "done when" criterion: generate a key, push it to a
 * server, then authenticate with it — exercised against the Docker test sshd
 * (`coffeessh-test-sshd`, user `coffeetest` / password `coffeetest123`).
 *
 * These tests are skipped (JUnit assumptions) when the container is not reachable,
 * so the plain unit-test run stays hermetic.
 */
class SshCopyKeyIntegrationTest {

    /** In-memory [KnownHostDao]: Room isn't available (or needed) on the unit-test JVM. */
    private class InMemoryKnownHostDao : KnownHostDao {
        val entries = mutableListOf<KnownHostEntity>()

        override suspend fun findFor(host: String, port: Int): List<KnownHostEntity> =
            entries.filter { it.host == host && it.port == port }

        override suspend fun upsert(knownHost: KnownHostEntity): Long {
            entries.removeAll { it.host == knownHost.host && it.port == knownHost.port && it.keyType == knownHost.keyType }
            entries += knownHost
            return knownHost.id
        }

        override suspend fun delete(knownHost: KnownHostEntity) {
            entries.remove(knownHost)
        }
    }

    companion object {
        private val HOST = System.getenv("COFFEESSH_TEST_HOST") ?: "127.0.0.1"
        private val PORT = (System.getenv("COFFEESSH_TEST_PORT") ?: "2222").toInt()
        private val USER = System.getenv("COFFEESSH_TEST_USER") ?: "coffeetest"
        private val PASSWORD = System.getenv("COFFEESSH_TEST_PASSWORD") ?: "coffeetest123"

        @BeforeClass
        @JvmStatic
        fun installBouncyCastle() {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Before
    fun assumeServerReachable() {
        val reachable = runCatching {
            Socket().use { it.connect(InetSocketAddress(HOST, PORT), 2_000) }
        }.isSuccess
        assumeTrue("test sshd not reachable at $HOST:$PORT - start coffeessh-test-sshd", reachable)
    }

    /** Removes any authorized_keys line whose comment starts with coffeessh- (test hygiene). */
    private fun removeGeneratedKeys() {
        val client = SSHClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(HOST, PORT)
        try {
            client.authPassword(USER, PASSWORD)
            val session = client.startSession()
            try {
                session.exec("sed -i '/ coffeessh-/d' ~/.ssh/authorized_keys").join(10, TimeUnit.SECONDS)
            } finally {
                session.close()
            }
        } finally {
            client.disconnect()
        }
    }

    private fun execWithKey(privateKeyPem: String, command: String): String {
        val client = SSHClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(HOST, PORT)
        try {
            client.authPublickey(USER, client.loadKeys(privateKeyPem, null, null))
            val session = client.startSession()
            try {
                val cmd = session.exec(command)
                val output = cmd.inputStream.bufferedReader().readText()
                cmd.join(10, TimeUnit.SECONDS)
                return output
            } finally {
                session.close()
            }
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `generate key, copy to server, then authenticate with it`() = runBlocking {
        removeGeneratedKeys()
        val dao = InMemoryKnownHostDao()
        val key = generateKey(KeyTypeOption.ED25519)
        val prompts = mutableListOf<TofuHostKeyVerifier.Result.NeedsDecision>()

        val result = SshCopyKeyExecutor.execute(
            host = HOST,
            port = PORT,
            username = USER,
            password = PASSWORD,
            privateKeyPem = null,
            publicKey = key.publicKey,
            hostKeyVerifier = TofuHostKeyVerifier(dao),
            onProgress = {},
            confirmHostKey = { decision -> prompts += decision; true },
        )

        assertTrue("copy should succeed, was: $result", result is CopyKeyResult.Success)
        // TOFU: first contact prompts (not changed) and pins the key on accept.
        assertEquals(1, prompts.size)
        assertEquals(false, prompts[0].isChanged)
        assertTrue(dao.entries.isNotEmpty())

        // The generated private key alone must now be enough to open a session.
        assertEquals(USER, execWithKey(key.privateKeyPem, "whoami").trim())
    }

    @Test
    fun `copying the same key twice does not duplicate it and needs no second prompt`() = runBlocking {
        removeGeneratedKeys()
        val dao = InMemoryKnownHostDao()
        val key = generateKey(KeyTypeOption.ED25519)

        suspend fun copy(confirm: suspend (TofuHostKeyVerifier.Result.NeedsDecision) -> Boolean) =
            SshCopyKeyExecutor.execute(
                host = HOST,
                port = PORT,
                username = USER,
                password = PASSWORD,
                privateKeyPem = null,
                publicKey = key.publicKey,
                hostKeyVerifier = TofuHostKeyVerifier(dao),
                onProgress = {},
                confirmHostKey = confirm,
            )

        assertTrue(copy { true } is CopyKeyResult.Success)

        var prompted = false
        val second = copy { prompted = true; true }
        assertTrue("second copy should report AlreadyPresent, was: $second", second is CopyKeyResult.AlreadyPresent)
        assertTrue("pinned host key should not prompt again", !prompted)

        val blob = SshCopyKeyExecutor.keyBlobOf(key.publicKey)!!
        val occurrences = execWithKey(key.privateKeyPem, "cat ~/.ssh/authorized_keys")
            .lines().count { it.contains(blob) }
        assertEquals(1, occurrences)
    }

    @Test
    fun `changed host key aborts the copy when the user rejects it`() = runBlocking {
        removeGeneratedKeys()
        val dao = InMemoryKnownHostDao()
        val key = generateKey(KeyTypeOption.ED25519)

        val first = SshCopyKeyExecutor.execute(
            host = HOST, port = PORT, username = USER, password = PASSWORD,
            privateKeyPem = null, publicKey = key.publicKey,
            hostKeyVerifier = TofuHostKeyVerifier(dao),
            onProgress = {},
            confirmHostKey = { true },
        )
        assertTrue(first is CopyKeyResult.Success)

        // Simulate a MITM-relevant change: the stored fingerprint no longer matches.
        dao.entries.replaceAll { it.copy(fingerprint = "SHA256:bogus") }

        var sawChanged = false
        val result = SshCopyKeyExecutor.execute(
            host = HOST, port = PORT, username = USER, password = PASSWORD,
            privateKeyPem = null, publicKey = key.publicKey,
            hostKeyVerifier = TofuHostKeyVerifier(dao),
            onProgress = {},
            confirmHostKey = { decision -> sawChanged = decision.isChanged; false },
        )
        assertTrue(sawChanged)
        assertTrue("rejected host key should fail the copy, was: $result", result is CopyKeyResult.Failed)
    }
}
