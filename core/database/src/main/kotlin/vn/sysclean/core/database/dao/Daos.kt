package vn.sysclean.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import vn.sysclean.core.database.model.PhotoSignatureEntity
import vn.sysclean.core.database.model.TrashEntity
import vn.sysclean.core.database.model.WhitelistEntity

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deleted_at DESC")
    fun observeAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TrashEntity>

    @Query("SELECT * FROM trash WHERE deleted_at < :before")
    suspend fun getOlderThan(before: Long): List<TrashEntity>

    @Query("SELECT * FROM trash")
    suspend fun getAll(): List<TrashEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TrashEntity)

    @Query("DELETE FROM trash WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM whitelist ORDER BY added_at DESC")
    fun observeAll(): Flow<List<WhitelistEntity>>

    @Query("SELECT path FROM whitelist")
    suspend fun getPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: WhitelistEntity)

    @Query("DELETE FROM whitelist WHERE path = :path")
    suspend fun delete(path: String)
}

@Dao
interface PhotoSignatureDao {
    @Query("SELECT * FROM photo_signature")
    suspend fun getAll(): List<PhotoSignatureEntity>

    @Upsert
    suspend fun upsert(entities: List<PhotoSignatureEntity>)

    @Query("DELETE FROM photo_signature WHERE media_id NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<Long>)
}
