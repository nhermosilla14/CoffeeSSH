package cl.segfault.coffeessh.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts/decrypts app secrets (passwords, private keys) with an AES-256-GCM key that
 * lives in the Android Keystore and never leaves the secure hardware/TEE.
 *
 * Blob layout: `[12-byte IV][ciphertext+tag]`.
 */
class KeystoreCrypto(private val alias: String = KEY_ALIAS) {

    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE) { "Encrypted blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_BITS, blob, 0, IV_SIZE)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    fun encryptString(plaintext: String): ByteArray = encrypt(plaintext.encodeToByteArray())

    fun decryptToString(blob: ByteArray): String = decrypt(blob).decodeToString()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "coffeessh-master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
