package vn.sysclean.core.model

/** The strongest way the app can reach the file system and other apps. */
enum class AccessLevel { NORMAL, SHIZUKU, ROOT }

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, PERMISSION_REQUIRED, READY }

data class AccessState(
    val allFilesAccess: Boolean,
    val usageAccess: Boolean,
    val shizuku: ShizukuState,
    val rootDetected: Boolean,
    /** The user turned root mode on and `su` granted it. */
    val rootMode: Boolean = false,
) {
    /** The mode features actually run in; Shizuku wins when both are available. */
    val activeLevel: AccessLevel
        get() = when {
            shizuku == ShizukuState.READY -> AccessLevel.SHIZUKU
            rootMode -> AccessLevel.ROOT
            else -> AccessLevel.NORMAL
        }

    /** Everything detected on the device, including root that no feature uses yet. */
    val availableLevel: AccessLevel
        get() = when {
            shizuku == ShizukuState.READY -> AccessLevel.SHIZUKU
            rootDetected -> AccessLevel.ROOT
            else -> AccessLevel.NORMAL
        }

    val hasRequiredPermissions: Boolean get() = allFilesAccess && usageAccess

    companion object {
        val Unknown = AccessState(
            allFilesAccess = false,
            usageAccess = false,
            shizuku = ShizukuState.NOT_INSTALLED,
            rootDetected = false,
        )
    }
}
