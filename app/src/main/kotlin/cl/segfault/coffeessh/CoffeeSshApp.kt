package cl.segfault.coffeessh

import android.app.Application
import android.content.Context
import cl.segfault.coffeessh.data.crypto.KeystoreCrypto
import cl.segfault.coffeessh.data.db.CoffeeDatabase
import cl.segfault.coffeessh.data.repo.ConnectionsRepository
import cl.segfault.coffeessh.data.repo.GroupsRepository
import cl.segfault.coffeessh.data.repo.IdentitiesRepository
import cl.segfault.coffeessh.ssh.SshSessionRegistry
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Hand-rolled dependency container; small app, no DI framework needed. */
class AppContainer(context: Context) {
    private val database = CoffeeDatabase.build(context)

    val crypto = KeystoreCrypto()
    val connectionsRepository = ConnectionsRepository(database.connectionDao(), database.connectionLogDao())
    val identitiesRepository = IdentitiesRepository(database.identityDao(), crypto)
    val groupsRepository = GroupsRepository(database.groupDao())
    val knownHostDao = database.knownHostDao()
    val sshSessionRegistry = SshSessionRegistry(knownHostDao)
}

class CoffeeSshApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        registerBouncyCastle()
        container = AppContainer(this)
    }

    /**
     * Android ships a stripped-down "BC" provider missing algorithms sshj needs (notably
     * some Ed25519/modern KDF paths). Replace it with the full BouncyCastle from our own
     * dependency, taking priority over whatever the platform registered.
     */
    private fun registerBouncyCastle() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
