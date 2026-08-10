package cl.segfault.coffeessh.ssh

import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Base64

enum class KeyTypeOption(
    val label: String,
    val sshName: String,
) {
    ED25519("Ed25519", "Ed25519"),
    ECDSA_P256("ECDSA P-256", "ECDSA P-256"),
    ECDSA_P384("ECDSA P-384", "ECDSA P-384"),
    ECDSA_P521("ECDSA P-521", "ECDSA P-521"),
    RSA_2048("RSA 2048", "RSA 2048"),
    RSA_3072("RSA 3072", "RSA 3072"),
    RSA_4096("RSA 4096", "RSA 4096");

    val keyTypeDb: String get() = name.lowercase()
}

data class GeneratedKey(
    val privateKeyPem: String,
    val publicKey: String,
    val keyType: KeyTypeOption,
)

fun generateKey(type: KeyTypeOption): GeneratedKey {
    val keyPair = generateKeyPair(type)
    val privateKeyPem = toOpenSshPrivateKey(keyPair)
    val publicKeyOpenSsh = toOpenSshPublicKey(keyPair.public)
    return GeneratedKey(
        privateKeyPem = privateKeyPem,
        publicKey = publicKeyOpenSsh,
        keyType = type,
    )
}

private fun generateKeyPair(type: KeyTypeOption): KeyPair {
    val kpg = when (type) {
        KeyTypeOption.ED25519 -> KeyPairGenerator.getInstance("Ed25519", "BC")
        KeyTypeOption.ECDSA_P256 -> KeyPairGenerator.getInstance("EC", "BC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }
        KeyTypeOption.ECDSA_P384 -> KeyPairGenerator.getInstance("EC", "BC").apply {
            initialize(ECGenParameterSpec("secp384r1"))
        }
        KeyTypeOption.ECDSA_P521 -> KeyPairGenerator.getInstance("EC", "BC").apply {
            initialize(ECGenParameterSpec("secp521r1"))
        }
        KeyTypeOption.RSA_2048 -> KeyPairGenerator.getInstance("RSA", "BC").apply {
            initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
        }
        KeyTypeOption.RSA_3072 -> KeyPairGenerator.getInstance("RSA", "BC").apply {
            initialize(RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4))
        }
        KeyTypeOption.RSA_4096 -> KeyPairGenerator.getInstance("RSA", "BC").apply {
            initialize(RSAKeyGenParameterSpec(4096, RSAKeyGenParameterSpec.F4))
        }
    }
    return kpg.generateKeyPair()
}

/**
 * Serializes to the OpenSSH private key format ("BEGIN OPENSSH PRIVATE KEY"), NOT PKCS8
 * PEM: sshj's PKCS8 parser only understands RSA/EC/DSA, so a PKCS8-encoded Ed25519 key
 * would fail to load back for authentication. Unencrypted "none"/"none" cipher+kdf —
 * at-rest encryption is the app's Keystore layer's job, not this file format's.
 */
private fun toOpenSshPrivateKey(keyPair: KeyPair): String {
    val publicBlob = sshPublicKeyBlob(keyPair.public)
    val keyType = KeyType.fromKey(keyPair.public).toString()
    val check = SecureRandom().nextInt()
    val privateBlock = Buffer.PlainBuffer()
        .putUInt32FromInt(check)
        .putUInt32FromInt(check)
        .putString(keyType)

    when (KeyType.fromKey(keyPair.public)) {
        KeyType.RSA -> {
            val privateKey = keyPair.private as RSAPrivateCrtKey
            privateBlock
                .putMPInt(privateKey.modulus)
                .putMPInt(privateKey.publicExponent)
                .putMPInt(privateKey.privateExponent)
                .putMPInt(privateKey.crtCoefficient)
                .putMPInt(privateKey.primeP)
                .putMPInt(privateKey.primeQ)
        }
        KeyType.ECDSA256, KeyType.ECDSA384, KeyType.ECDSA521 -> {
            val publicKey = keyPair.public as ECPublicKey
            val privateKey = keyPair.private as ECPrivateKey
            privateBlock
                .putString(curveName(KeyType.fromKey(publicKey)))
                .putBytes(ecPoint(publicKey))
                .putMPInt(privateKey.s)
        }
        KeyType.ED25519 -> {
            val publicKey = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded).publicKeyData.bytes
            val seed = ASN1OctetString.getInstance(
                PrivateKeyInfo.getInstance(keyPair.private.encoded).parsePrivateKey(),
            ).octets
            privateBlock.putBytes(publicKey).putBytes(seed + publicKey)
        }
        else -> error("Unsupported key type: ${KeyType.fromKey(keyPair.public)}")
    }

    privateBlock.putString("")
    val used = privateBlock.compactData
    val paddingLength = 8 - (used.size % 8)
    val paddedPrivateBlock = used + ByteArray(paddingLength) { index -> (index + 1).toByte() }

    val outer = Buffer.PlainBuffer()
        .putRawBytes("openssh-key-v1\u0000".toByteArray())
        .putString("none")
        .putString("none")
        .putString("")
        .putUInt32(1)
        .putBytes(publicBlob)
        .putBytes(paddedPrivateBlock)

    val encoded = Base64.getMimeEncoder(70, byteArrayOf('\n'.code.toByte()))
        .encodeToString(outer.compactData)
    return "-----BEGIN OPENSSH PRIVATE KEY-----\n$encoded\n-----END OPENSSH PRIVATE KEY-----\n"
}

fun formatOpenSshPublicKey(publicKey: PublicKey): String {
    val keyType = KeyType.fromKey(publicKey)
    val name = keyType.toString()
    val b64 = Base64.getEncoder().encodeToString(sshPublicKeyBlob(publicKey))
    return "$name $b64 coffeessh-${keyType.name.lowercase()}"
}

internal fun sshPublicKeyBlob(publicKey: PublicKey): ByteArray {
    if (isEd25519(publicKey)) {
        return Buffer.PlainBuffer()
            .putString("ssh-ed25519")
            .putBytes(SubjectPublicKeyInfo.getInstance(publicKey.encoded).publicKeyData.bytes)
            .compactData
    }
    val keyType = KeyType.fromKey(publicKey)
    val name = keyType.toString()
    val buf = Buffer.PlainBuffer().putString(name)
    when (keyType) {
        KeyType.RSA -> {
            val rsa = publicKey as RSAPublicKey
            buf.putMPInt(rsa.publicExponent)
            buf.putMPInt(rsa.modulus)
        }
        KeyType.ECDSA256, KeyType.ECDSA384, KeyType.ECDSA521 -> {
            val ec = publicKey as ECPublicKey
            val curveName = when (keyType) {
                KeyType.ECDSA256 -> "nistp256"
                KeyType.ECDSA384 -> "nistp384"
                KeyType.ECDSA521 -> "nistp521"
                else -> error("unreachable")
            }
            buf.putString(curveName)
            val params = ec.params
            val fieldSize = (params.curve.field.fieldSize + 7) / 8
            buf.putBytes(ecPoint(ec))
        }
        KeyType.ED25519 -> {
            val spki = SubjectPublicKeyInfo.getInstance(publicKey.encoded)
            buf.putBytes(spki.publicKeyData.bytes)
        }
        else -> error("Unsupported key type: $keyType")
    }
    return buf.compactData
}

internal fun sshPublicKeyType(publicKey: PublicKey): String =
    if (isEd25519(publicKey)) "ssh-ed25519" else KeyType.fromKey(publicKey).toString()

private fun isEd25519(publicKey: PublicKey): Boolean =
    publicKey.algorithm in setOf("Ed25519", "EdDSA", "EdEC") ||
        publicKey.javaClass.name.contains("EdDsa", ignoreCase = true) ||
        publicKey.javaClass.name.contains("Ed25519", ignoreCase = true)

private fun curveName(keyType: KeyType): String = when (keyType) {
    KeyType.ECDSA256 -> "nistp256"
    KeyType.ECDSA384 -> "nistp384"
    KeyType.ECDSA521 -> "nistp521"
    else -> error("Not an ECDSA key: $keyType")
}

private fun ecPoint(publicKey: ECPublicKey): ByteArray {
    val fieldSize = (publicKey.params.curve.field.fieldSize + 7) / 8
    return byteArrayOf(0x04) + fixedUnsigned(publicKey.w.affineX.toByteArray(), fieldSize) +
        fixedUnsigned(publicKey.w.affineY.toByteArray(), fieldSize)
}

private fun fixedUnsigned(value: ByteArray, size: Int): ByteArray {
    val unsigned = if (value.size > 1 && value[0] == 0.toByte()) value.copyOfRange(1, value.size) else value
    require(unsigned.size <= size) { "EC coordinate exceeds curve size" }
    return ByteArray(size - unsigned.size) + unsigned
}

private fun toOpenSshPublicKey(publicKey: PublicKey): String = formatOpenSshPublicKey(publicKey)
