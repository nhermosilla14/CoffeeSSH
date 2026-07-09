package cl.segfault.coffeessh.data.repo

import cl.segfault.coffeessh.data.db.GroupDao
import cl.segfault.coffeessh.data.db.GroupEntity
import kotlinx.coroutines.flow.Flow

class GroupsRepository(private val dao: GroupDao) {

    fun observeAll(): Flow<List<GroupEntity>> = dao.observeAll()

    suspend fun create(name: String): Long = dao.insert(GroupEntity(name = name.trim()))

    suspend fun rename(group: GroupEntity, newName: String) =
        dao.update(group.copy(name = newName.trim()))

    suspend fun delete(id: Long) = dao.delete(id)
}
