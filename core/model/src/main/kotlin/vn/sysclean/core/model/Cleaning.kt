package vn.sysclean.core.model

data class TrashItem(
    val id: String,
    val originalPath: String,
    /** Where the item currently lives inside the bin. */
    val trashPath: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val category: JunkCategory?,
    val deletedAt: Long,
    /** When the item will be removed for good. */
    val expiresAt: Long,
) {
    val name: String get() = originalPath.substringAfterLast('/')
}

data class WhitelistEntry(val path: String, val addedAt: Long)

data class CleanResult(
    val cleanedCount: Int,
    val cleanedBytes: Long,
    val failedCount: Int,
    /** Duplicates whose full hash did not match the kept copy, so they were left alone. */
    val unverifiedCount: Int,
    /** Recycle-bin ids of what was moved, for undo. Empty folders are deleted outright. */
    val trashIds: List<String>,
) {
    operator fun plus(other: CleanResult) = CleanResult(
        cleanedCount + other.cleanedCount,
        cleanedBytes + other.cleanedBytes,
        failedCount + other.failedCount,
        unverifiedCount + other.unverifiedCount,
        trashIds + other.trashIds,
    )

    companion object {
        val Empty = CleanResult(0, 0, 0, 0, emptyList())
    }
}
