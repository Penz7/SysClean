package vn.sysclean.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import vn.sysclean.core.database.dao.PhotoSignatureDao
import vn.sysclean.core.database.dao.TrashDao
import vn.sysclean.core.database.dao.WhitelistDao
import vn.sysclean.core.database.model.PhotoSignatureEntity
import vn.sysclean.core.database.model.TrashEntity
import vn.sysclean.core.database.model.WhitelistEntity

@Database(
    entities = [TrashEntity::class, WhitelistEntity::class, PhotoSignatureEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 1 -> 2: photo signatures gained structure + algorithm version.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class SysCleanDatabase : RoomDatabase() {
    abstract fun trashDao(): TrashDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun photoSignatureDao(): PhotoSignatureDao
}
