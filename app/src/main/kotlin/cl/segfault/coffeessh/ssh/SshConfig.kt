package cl.segfault.coffeessh.ssh

import com.hierynomus.sshj.transport.cipher.BlockCiphers
import com.hierynomus.sshj.transport.cipher.GcmCiphers
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.Config
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper

/** SSHJ configuration with ciphers known to be available on Android. */
fun coffeeSshConfig(): Config = AndroidConfig().apply {
    setCipherFactories(
        BlockCiphers.AES128CTR(),
        BlockCiphers.AES192CTR(),
        BlockCiphers.AES256CTR(),
        GcmCiphers.AES128GCM(),
        GcmCiphers.AES256GCM(),
    )
}

/** Parse Ed25519 keys with BC while leaving Android's provider for X25519/kex. */
fun loadCoffeeSshKeys(client: SSHClient, privateKeyPem: String): KeyProvider {
    return synchronized(SecurityUtils::class.java) {
        // SSHJ's OpenSSH parser uses SecurityUtils for Ed25519. Keep BC scoped to
        // parsing so Android's provider remains available for X25519 transport.
        SecurityUtils.setSecurityProvider("BC")
        try {
            val parsed = client.loadKeys(privateKeyPem, null, null)
            KeyPairWrapper(parsed.public, parsed.private)
        } finally {
            SecurityUtils.setSecurityProvider(null)
        }
    }
}
