package vn.sysclean.core.model

data class StorageVolumeInfo(
    val label: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = totalBytes - freeBytes
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

/** Breakdown of the primary volume by content type. */
data class StorageBreakdown(
    val appsBytes: Long,
    val imagesBytes: Long,
    val videosBytes: Long,
    val audioBytes: Long,
    val otherBytes: Long,
    val systemBytes: Long,
)

data class StorageInfo(
    val volumes: List<StorageVolumeInfo>,
    /** Null when the system refuses the query, typically because usage access is missing. */
    val breakdown: StorageBreakdown?,
) {
    val primary: StorageVolumeInfo? get() = volumes.firstOrNull { it.isPrimary }
}
