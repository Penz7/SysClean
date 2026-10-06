package vn.sysclean.core.model

enum class JunkCategory(
    /** Safe junk can be removed without the user losing anything they care about. */
    val isSafeToClean: Boolean,
) {
    APP_CACHE(true),
    TEMP_FILES(true),
    THUMBNAILS(true),
    APK_FILES(true),
    EMPTY_FOLDERS(true),
    LEFTOVER_FOLDERS(true),
    DUPLICATE_FILES(false),
    SIMILAR_PHOTOS(false),
    BLURRY_PHOTOS(false),
    LARGE_FILES(false),
    SCREENSHOTS(false),
    OLD_DOWNLOADS(false),
}

enum class JunkNote { APK_INSTALLED, APK_NEWER_THAN_INSTALLED, APK_NOT_INSTALLED, APK_INVALID }

data class JunkItem(
    val path: String,
    val title: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val note: JunkNote? = null,
    /** Items in the same duplicate set share an id. */
    val groupId: String? = null,
    /** In a duplicate or similar-photo set, the copy that would be kept. */
    val isOriginal: Boolean = false,
    /** MediaStore id for photos, used to load thumbnails. */
    val mediaId: Long? = null,
    /**
     * App cache with Shizuku: [sizeBytes] is the part that can be cleared for this app alone
     * (Android/data/<pkg>/cache); this is its whole cache, internal part included.
     */
    val fullCacheBytes: Long? = null,
)

data class JunkGroup(
    val category: JunkCategory,
    val itemCount: Int,
    val totalBytes: Long,
    /** Largest items first, capped so a huge device cannot blow up memory. */
    val items: List<JunkItem>,
)

data class JunkReport(
    val groups: List<JunkGroup>,
    val filesScanned: Long,
    val bytesScanned: Long,
    val durationMillis: Long,
    val finishedAt: Long,
    /** Categories that were skipped for lack of permission. */
    val skipped: Set<JunkCategory>,
) {
    val safeBytes: Long get() = groups.filter { it.category.isSafeToClean }.sumOf { it.totalBytes }
    val reviewBytes: Long get() = groups.filterNot { it.category.isSafeToClean }.sumOf { it.totalBytes }
}

data class ScanProgress(
    val currentPath: String,
    val filesScanned: Long,
    val bytesScanned: Long,
)

/** Categories whose items are app packages rather than files. */
val JunkCategory.isAppBased: Boolean get() = this == JunkCategory.APP_CACHE

/** Categories that come in sets where one member is kept (duplicates, similar photos). */
val JunkCategory.isGrouped: Boolean
    get() = this == JunkCategory.DUPLICATE_FILES || this == JunkCategory.SIMILAR_PHOTOS

val JunkCategory.isPhotoBased: Boolean
    get() = this == JunkCategory.SIMILAR_PHOTOS || this == JunkCategory.BLURRY_PHOTOS || this == JunkCategory.SCREENSHOTS

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val progress: ScanProgress) : ScanState
    data class Finished(val report: JunkReport) : ScanState
    data class Failed(val message: String) : ScanState
}
