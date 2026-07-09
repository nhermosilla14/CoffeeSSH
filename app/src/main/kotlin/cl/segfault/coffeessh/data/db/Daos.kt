package cl.segfault.coffeessh.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {

    @Transaction
    @Query("SELECT * FROM connections ORDER BY sortOrder, COALESCE(nickname, host) COLLATE NOCASE")
    fun observeAllWithRefs(): Flow<List<ConnectionWithRefs>>

    @Transaction
    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun getWithRefs(id: Long): ConnectionWithRefs?

    @Transaction
    @Query(
        """
        SELECT c.* FROM connections c
        INNER JOIN connection_logs l ON l.connectionId = c.id
        GROUP BY c.id
        ORDER BY COUNT(l.id) DESC, MAX(l.connectedAt) DESC
        LIMIT :limit
        """,
    )
    fun observeFrequent(limit: Int): Flow<List<ConnectionWithRefs>>

    @Insert
    suspend fun insert(connection: ConnectionEntity): Long

    @Update
    suspend fun update(connection: ConnectionEntity)

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM connection_groups WHERE connectionId = :connectionId")
    suspend fun clearGroups(connectionId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroupRefs(refs: List<ConnectionGroupCrossRef>)

    @Transaction
    suspend fun replaceGroups(connectionId: Long, groupIds: List<Long>) {
        clearGroups(connectionId)
        if (groupIds.isNotEmpty()) {
            insertGroupRefs(groupIds.map { ConnectionGroupCrossRef(connectionId, it) })
        }
    }
}

@Dao
interface IdentityDao {

    @Query("SELECT * FROM identities ORDER BY nickname COLLATE NOCASE")
    fun observeAll(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun getById(id: Long): IdentityEntity?

    @Insert
    suspend fun insert(identity: IdentityEntity): Long

    @Update
    suspend fun update(identity: IdentityEntity)

    @Query("DELETE FROM identities WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<GroupEntity>>

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ConnectionLogDao {

    @Insert
    suspend fun insert(log: ConnectionLogEntity): Long

    @Query("SELECT COUNT(*) FROM connection_logs WHERE connectionId = :connectionId")
    suspend fun countFor(connectionId: Long): Int
}

@Dao
interface KnownHostDao {

    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun findFor(host: String, port: Int): List<KnownHostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(knownHost: KnownHostEntity): Long

    @Delete
    suspend fun delete(knownHost: KnownHostEntity)
}
