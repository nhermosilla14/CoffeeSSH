package cl.segfault.coffeessh

import android.app.Application
import android.content.Context
import cl.segfault.coffeessh.data.crypto.KeystoreCrypto
import cl.segfault.coffeessh.data.db.CoffeeDatabase
import cl.segfault.coffeessh.data.repo.ConnectionsRepository
import cl.segfault.coffeessh.data.repo.GroupsRepository
import cl.segfault.coffeessh.data.repo.IdentitiesRepository

/** Hand-rolled dependency container; small app, no DI framework needed. */
class AppContainer(context: Context) {
    private val database = CoffeeDatabase.build(context)

    val crypto = KeystoreCrypto()
    val connectionsRepository = ConnectionsRepository(database.connectionDao(), database.connectionLogDao())
    val identitiesRepository = IdentitiesRepository(database.identityDao(), crypto)
    val groupsRepository = GroupsRepository(database.groupDao())
    val knownHostDao = database.knownHostDao()
}

class CoffeeSshApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
