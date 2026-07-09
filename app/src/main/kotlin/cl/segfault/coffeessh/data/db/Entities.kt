package cl.segfault.coffeessh.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val username: String,
    val passwordEnc: ByteArray? = null,
    val privateKeyEnc: ByteArray? = null,
    val publicKey: String? = null,
    val keyType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "connections",
    foreignKeys = [
        ForeignKey(
            entity = IdentityEntity::class,
            parentColumns = ["id"],
            childColumns = ["identityId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("identityId")],
)
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String? = null,
    val host: String,
    val port: Int = 22,
    val identityId: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "connection_groups",
    primaryKeys = ["connectionId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = ConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class ConnectionGroupCrossRef(
    val connectionId: Long,
    val groupId: Long,
)

@Entity(
    tableName = "connection_logs",
    foreignKeys = [
        ForeignKey(
            entity = ConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("connectionId")],
)
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val connectionId: Long,
    val connectedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "known_hosts",
    indices = [Index(value = ["host", "port", "keyType"], unique = true)],
)
data class KnownHostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprint: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** A connection together with its (optional) identity and the groups it belongs to. */
data class ConnectionWithRefs(
    @Embedded val connection: ConnectionEntity,
    @Relation(parentColumn = "identityId", entityColumn = "id")
    val identity: IdentityEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ConnectionGroupCrossRef::class,
            parentColumn = "connectionId",
            entityColumn = "groupId",
        ),
    )
    val groups: List<GroupEntity>,
)
