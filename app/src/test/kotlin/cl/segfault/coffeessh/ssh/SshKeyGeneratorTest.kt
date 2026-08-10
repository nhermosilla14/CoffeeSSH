package cl.segfault.coffeessh.ssh

import java.security.Security
import java.util.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class SshKeyGeneratorTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun installBouncyCastle() {
            // Mirror CoffeeSshApp.registerBouncyCastle(): unit-test JVMs don't run the
            // Application class, and the test asserts generation via the "BC" provider.
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun `generates every advertised key type with the correct OpenSSH algorithm name`() {
        val expectedPrefix = mapOf(
            KeyTypeOption.ED25519 to "ssh-ed25519",
            KeyTypeOption.ECDSA_P256 to "ecdsa-sha2-nistp256",
            KeyTypeOption.ECDSA_P384 to "ecdsa-sha2-nistp384",
            KeyTypeOption.ECDSA_P521 to "ecdsa-sha2-nistp521",
            KeyTypeOption.RSA_2048 to "ssh-rsa",
            KeyTypeOption.RSA_3072 to "ssh-rsa",
            KeyTypeOption.RSA_4096 to "ssh-rsa",
        )
        for ((type, prefix) in expectedPrefix) {
            val key = generateKey(type)
            assertTrue(
                "public key for $type should start with $prefix, was: ${key.publicKey.take(40)}",
                key.publicKey.startsWith("$prefix "),
            )
            assertTrue(
                "public key for $type should carry a coffeessh-* comment, was: ${key.publicKey.takeLast(30)}",
                key.publicKey.trim().split(Regex("\\s+")).last().startsWith("coffeessh-"),
            )
            assertTrue(
                "private key for $type should be PEM",
                key.privateKeyPem.contains("BEGIN") && key.privateKeyPem.contains("PRIVATE KEY"),
            )
            assertEquals(type, key.keyType)
        }
    }

    @Test
    fun `public key blob is decodable and self-describing`() {
        val key = generateKey(KeyTypeOption.ED25519)
        val (algo, blobB64) = key.publicKey.trim().split(Regex("\\s+"))
        val blob = Base64.getDecoder().decode(blobB64)
        // SSH wire format: the blob's first field is the algorithm name as a length-prefixed string.
        val nameLen = (blob[0].toInt() shl 24) or (blob[1].toInt() shl 16) or (blob[2].toInt() shl 8) or blob[3].toInt()
        val name = String(blob, 4, nameLen)
        assertEquals(algo, name)
        assertEquals("ssh-ed25519", name)
        // No trailing garbage: 4 + 11 (algo) + 4 + 32 (raw Ed25519 point) = 51 bytes exactly.
        // (Regresses the PlainBuffer.array()-instead-of-compactData padding bug.)
        assertEquals(51, blob.size)
    }

    @Test
    fun `generated keys are unique per invocation`() {
        val a = generateKey(KeyTypeOption.ECDSA_P256)
        val b = generateKey(KeyTypeOption.ECDSA_P256)
        assertTrue(a.publicKey != b.publicKey)
        assertTrue(a.privateKeyPem != b.privateKeyPem)
    }

    @Test
    fun `sshj loads our PEM back to the same public key`() {
        for (type in KeyTypeOption.entries) {
            val key = generateKey(type)
            val client = net.schmizz.sshj.SSHClient()
            val provider = client.loadKeys(key.privateKeyPem, null, null)
            val reloaded = formatOpenSshPublicKey(provider.public)
            // Same blob; the comment may differ, so compare algorithm + blob only.
            assertEquals(
                "public key mismatch after PEM round-trip for $type",
                key.publicKey.trim().split(Regex("\\s+")).take(2),
                reloaded.trim().split(Regex("\\s+")).take(2),
            )
        }
    }

    @Test
    fun `keyBlobOf extracts the base64 field from an OpenSSH public key line`() {
        val key = generateKey(KeyTypeOption.ED25519)
        val expected = key.publicKey.trim().split(Regex("\\s+"))[1]
        assertEquals(expected, SshCopyKeyExecutor.keyBlobOf(key.publicKey))
        assertEquals(expected, SshCopyKeyExecutor.keyBlobOf("  ${key.publicKey}  \n"))
        assertEquals(null, SshCopyKeyExecutor.keyBlobOf(""))
        assertEquals(null, SshCopyKeyExecutor.keyBlobOf("ssh-ed25519"))
    }
}
