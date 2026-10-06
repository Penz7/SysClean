package vn.sysclean.core.model

data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val targetSdk: Int,
    val installedAt: Long,
    val updatedAt: Long,
    /** Null when usage access has not been granted. */
    val lastUsedAt: Long?,
    val size: AppSize?,
)

data class AppSize(
    val apkBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    val totalBytes: Long get() = apkBytes + dataBytes
}

/** State of a pre-installed package for the current user. */
data class PreinstalledApp(
    val packageName: String,
    val label: String,
    /** False after "uninstall for this user"; the APK is still on the system partition. */
    val installedForUser: Boolean,
    /**
     * Turned off by the user (or by us on their behalf). Packages the manufacturer ships as
     * "disabled until used" are not counted: nothing was done to them.
     */
    val disabledByUser: Boolean,
) {
    val isRemoved: Boolean get() = !installedForUser || disabledByUser
}
