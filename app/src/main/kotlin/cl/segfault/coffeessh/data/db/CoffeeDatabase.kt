package cl.segfault.coffeessh.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        IdentityEntity::class,
        GroupEntity::class,
        ConnectionEntity::class,
        ConnectionGroupCrossRef::class,
        ConnectionLogEntity::class,
        KnownHostEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CoffeeDatabase : RoomDatabase() {

    abstract fun connectionDao(): ConnectionDao
    abstract fun identityDao(): IdentityDao
    abstract fun groupDao(): GroupDao
    abstract fun connectionLogDao(): ConnectionLogDao
    abstract fun knownHostDao(): KnownHostDao

    companion object {
        fun build(context: Context): CoffeeDatabase =
            Room.databaseBuilder(context, CoffeeDatabase::class.java, "coffeessh.db")
                .build()
    }
}
