package vn.sysclean.core.privilege.shell

import java.io.File

/**
 * The only places the privileged service may delete from. Kept as plain Kotlin so the
 * guard is unit-tested; a bug elsewhere must never let the shell identity wipe arbitrary files.
 */
object PrivilegedPaths {
    const val STORAGE_ROOT = "/storage/emulated/0"
    const val ANDROID_DATA = "$STORAGE_ROOT/Android/data"
    const val ANDROID_OBB = "$STORAGE_ROOT/Android/obb"

    private val deletableRoots = listOf(ANDROID_DATA, ANDROID_OBB)

    /** Binaries the service may run with any arguments. */
    val allowedCommands = setOf("pm", "am", "cmd", "id")

    private val dumpsysServices = setOf("meminfo", "gfxinfo", "package")

    /**
     * Whole-command allowlist. Beyond pm/am/cmd, a few read-only or maintenance commands are
     * allowed only in the exact shape SysClean uses them.
     */
    fun isCommandAllowed(command: List<String>): Boolean {
        val binary = command.firstOrNull() ?: return false
        return when (binary) {
            in allowedCommands -> true
            // Reading memory, frame and compile stats.
            "dumpsys" -> command.getOrNull(1) in dumpsysServices ||
                // Battery use, read-only: never --reset or any other option that changes it.
                command.drop(1) == listOf("batterystats", "--checkin")
            // TRIM of the flash storage.
            "sm" -> command.drop(1) == listOf("fstrim")
            // Pressure stall information: how often apps waited for memory or I/O.
            "cat" -> command.size == 2 && command[1] in setOf("/proc/pressure/memory", "/proc/pressure/io")
            // Only ever a plain reboot, always after the user confirmed it.
            "svc" -> command.drop(1) == listOf("power", "reboot")
            else -> false
        }
    }

    /**
     * Drops output rows nobody reads before they cross Binder. Battery stats are the only case:
     * per-wakelock, per-process and per-sensor rows are most of the dump and, after a few days
     * on battery, would exceed what a Binder transaction can carry.
     */
    fun keepsOutputLine(command: List<String>, line: String): Boolean {
        if (command != BATTERY_CHECKIN) return true
        return line.split(',', limit = 5).getOrNull(3) in batteryRows
    }

    private val BATTERY_CHECKIN = listOf("dumpsys", "batterystats", "--checkin")
    private val batteryRows = setOf("uid", "bt", "pws", "pwi", "wua", "awl", "cpu", "st")

    /** True for a package folder or anything inside it, never for the roots themselves. */
    fun isDeletable(path: String): Boolean {
        val normalized = File(path).normalize().path
        if (normalized != path.trimEnd('/')) return false // reject "..", "//" tricks outright
        return deletableRoots.any { root ->
            normalized.startsWith("$root/") && normalized.removePrefix("$root/").isNotBlank()
        }
    }

    fun isUnderPrivilegedRoot(path: String): Boolean = deletableRoots.any { path == it || path.startsWith("$it/") }

    fun externalCacheOf(packageName: String) = "$ANDROID_DATA/$packageName/cache"

    /** Private cache folders of [packageName] for the main user; reachable only with root. */
    fun internalCachesOf(packageName: String) = listOf(
        "/data/data/$packageName/cache",
        "/data/data/$packageName/code_cache",
        "/data/user_de/0/$packageName/cache",
        "/data/user_de/0/$packageName/code_cache",
    )

    private val internalCache = Regex("^/data/(data|user/\\d+|user_de/\\d+)/[A-Za-z][A-Za-z0-9_.]*/(cache|code_cache)$")

    /** An app's private cache folder itself (root may empty it, never delete it). */
    fun isInternalCacheDir(path: String): Boolean =
        File(path).normalize().path == path && internalCache.matches(path) && looksLikePackage(path.split('/')[path.split('/').size - 2])

    private val packageName = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    /** Folder names in Android/data are package names; ".nomedia" and the like are not. */
    fun looksLikePackage(name: String): Boolean = packageName.matches(name)
}
