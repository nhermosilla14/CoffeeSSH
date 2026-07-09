package cl.segfault.coffeessh.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoffeeDatabaseTest {

    private lateinit var db: CoffeeDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CoffeeDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun connectionWithRefsRoundTrip() = runBlocking {
        val identityId = db.identityDao().insert(IdentityEntity(nickname = "admin", username = "root"))
        val groupId = db.groupDao().insert(GroupEntity(name = "Servers"))
        val connectionId = db.connectionDao().insert(
            ConnectionEntity(nickname = "web", host = "example.com", port = 2222, identityId = identityId),
        )
        db.connectionDao().replaceGroups(connectionId, listOf(groupId))

        val all = db.connectionDao().observeAllWithRefs().first()
        assertEquals(1, all.size)
        val item = all.single()
        assertEquals("example.com", item.connection.host)
        assertEquals(2222, item.connection.port)
        assertEquals("root", item.identity?.username)
        assertEquals(listOf("Servers"), item.groups.map { it.name })
    }

    @Test
    fun replaceGroupsOverwritesPreviousMembership() = runBlocking {
        val g1 = db.groupDao().insert(GroupEntity(name = "One"))
        val g2 = db.groupDao().insert(GroupEntity(name = "Two"))
        val connectionId = db.connectionDao().insert(ConnectionEntity(host = "h"))

        db.connectionDao().replaceGroups(connectionId, listOf(g1))
        db.connectionDao().replaceGroups(connectionId, listOf(g2))

        val item = db.connectionDao().getWithRefs(connectionId)
        assertEquals(listOf("Two"), item?.groups?.map { it.name })
    }

    @Test
    fun deletingIdentityKeepsConnection() = runBlocking {
        val identityId = db.identityDao().insert(IdentityEntity(nickname = "temp", username = "u"))
        val connectionId = db.connectionDao().insert(ConnectionEntity(host = "h", identityId = identityId))

        db.identityDao().delete(identityId)

        val item = db.connectionDao().getWithRefs(connectionId)
        assertNotNull(item)
        assertNull(item?.connection?.identityId)
        assertNull(item?.identity)
    }

    @Test
    fun deletingGroupKeepsConnections() = runBlocking {
        val groupId = db.groupDao().insert(GroupEntity(name = "Doomed"))
        val connectionId = db.connectionDao().insert(ConnectionEntity(host = "h"))
        db.connectionDao().replaceGroups(connectionId, listOf(groupId))

        db.groupDao().delete(groupId)

        val item = db.connectionDao().getWithRefs(connectionId)
        assertNotNull(item)
        assertTrue(item!!.groups.isEmpty())
    }

    @Test
    fun frequentOrdersByLogCount() = runBlocking {
        val c1 = db.connectionDao().insert(ConnectionEntity(host = "once.example"))
        val c2 = db.connectionDao().insert(ConnectionEntity(host = "twice.example"))
        db.connectionLogDao().insert(ConnectionLogEntity(connectionId = c1))
        db.connectionLogDao().insert(ConnectionLogEntity(connectionId = c2))
        db.connectionLogDao().insert(ConnectionLogEntity(connectionId = c2))

        val frequent = db.connectionDao().observeFrequent(3).first()
        assertEquals(listOf("twice.example", "once.example"), frequent.map { it.connection.host })
    }
}
