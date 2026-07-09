package cl.segfault.coffeessh.data.repo

import cl.segfault.coffeessh.data.db.ConnectionDao
import cl.segfault.coffeessh.data.db.ConnectionEntity
import cl.segfault.coffeessh.data.db.ConnectionLogDao
import cl.segfault.coffeessh.data.db.ConnectionLogEntity
import cl.segfault.coffeessh.data.db.ConnectionWithRefs
import kotlinx.coroutines.flow.Flow

class ConnectionsRepository(
    private val dao: ConnectionDao,
    private val logDao: ConnectionLogDao,
) {

    fun observeAll(): Flow<List<ConnectionWithRefs>> = dao.observeAllWithRefs()

    fun observeFrequent(limit: Int = 3): Flow<List<ConnectionWithRefs>> = dao.observeFrequent(limit)

    suspend fun get(id: Long): ConnectionWithRefs? = dao.getWithRefs(id)

    suspend fun save(connection: ConnectionEntity, groupIds: List<Long>): Long {
        val id = if (connection.id == 0L) {
            dao.insert(connection)
        } else {
            dao.update(connection)
            connection.id
        }
        dao.replaceGroups(id, groupIds)
        return id
    }

    suspend fun duplicate(id: Long) {
        val source = dao.getWithRefs(id) ?: return
        val copyName = (source.connection.nickname ?: source.connection.host) + " (copy)"
        val newId = dao.insert(
            source.connection.copy(
                id = 0,
                nickname = copyName,
                createdAt = System.currentTimeMillis(),
            ),
        )
        dao.replaceGroups(newId, source.groups.map { it.id })
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun logConnected(connectionId: Long) {
        logDao.insert(ConnectionLogEntity(connectionId = connectionId))
    }
}
