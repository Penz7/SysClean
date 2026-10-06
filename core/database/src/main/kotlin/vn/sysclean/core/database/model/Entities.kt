package vn.sysclean.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A file or folder moved into the recycle bin, with everything needed to put it back. */
@Entity(tableName = "trash", indices = [Index("deleted_at")])
data class TrashEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "original_path") val originalPath: String,
    @ColumnInfo(name = "trash_path") val trashPath: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "is_directory") val isDirectory: Boolean,
    /** JunkCategory name, kept as text so the enum can evolve without a migration. */
    val category: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
)

/** A path (file, folder or package name) the user never wants reported as junk. */
@Entity(tableName = "whitelist")
data class WhitelistEntity(
    @PrimaryKey val path: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/**
 * Perceptual hash and sharpness of a photo. Keyed by MediaStore id and invalidated by
 * modification time + size, so rescans only decode new or edited photos.
 */
@Entity(tableName = "photo_signature")
data class PhotoSignatureEntity(
    @PrimaryKey @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "d_hash") val dHash: Long,
    val sharpness: Double,
    /** Standard deviation of brightness; near-uniform images have meaningless hashes. */
    val contrast: Double,
    /** Brightness spread of the 9x8 hash grid; low values mean the hash is unreliable. */
    @ColumnInfo(defaultValue = "0") val structure: Double,
    /** Signature algorithm version; rows from an older version are recomputed. */
    @ColumnInfo(defaultValue = "1") val algorithm: Int,
)
