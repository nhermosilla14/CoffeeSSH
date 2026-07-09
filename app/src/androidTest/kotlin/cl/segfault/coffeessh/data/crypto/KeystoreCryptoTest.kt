package cl.segfault.coffeessh.data.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException

@RunWith(AndroidJUnit4::class)
class KeystoreCryptoTest {

    private val crypto = KeystoreCrypto(alias = "coffeessh-test-key")

    @Test
    fun roundTripBytes() {
        val plaintext = ByteArray(256) { it.toByte() }
        val blob = crypto.encrypt(plaintext)
        assertArrayEquals(plaintext, crypto.decrypt(blob))
    }

    @Test
    fun roundTripString() {
        val secret = "correct horse battery staple"
        assertEquals(secret, crypto.decryptToString(crypto.encryptString(secret)))
    }

    @Test
    fun roundTripUnicode() {
        val secret = "contraseña-café-☕-密码"
        assertEquals(secret, crypto.decryptToString(crypto.encryptString(secret)))
    }

    @Test
    fun randomIvProducesDistinctBlobs() {
        val a = crypto.encryptString("same input")
        val b = crypto.encryptString("same input")
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun tamperedBlobFailsAuthentication() {
        val blob = crypto.encryptString("secret")
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
        assertThrows(GeneralSecurityException::class.java) {
            crypto.decrypt(blob)
        }
    }
}
