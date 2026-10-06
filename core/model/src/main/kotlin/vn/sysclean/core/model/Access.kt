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
    /** Shizuku has worked for SysClean before, so "not running" now means it was stopped (usually a reboot). */
    val shizukuWasSetUp: Boolean = false,
) {
    /** Shizuku used to work but is not running now: without root it stops at every reboot. */
    val shizukuStopped: Boolean
        get() = shizukuWasSetUp && shizuku == ShizukuState.NOT_RUNNING && !rootMode

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

/**
 * The phone-side switches Shizuku needs, read without any special permission so the setup
 * guide can tick steps off by itself.
 */
data class ShizukuSetupStatus(
    val developerOptions: Boolean,
    /** Null when this Android version does not let apps read it. */
    val wirelessDebugging: Boolean?,
    val wifiConnected: Boolean,
    val usbDebugging: Boolean,
)
