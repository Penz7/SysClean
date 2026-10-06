package vn.sysclean.core.privilege.shell

import javax.inject.Inject
import javax.inject.Singleton

/** App-level operations that need shell privileges. All act on the current user only. */
@Singleton
class SystemActions @Inject constructor(
    private val shell: PrivilegedShell,
) {
    val isReady: Boolean get() = shell.isReady

    suspend fun forceStop(packageName: String): Boolean =
        shell.run("am", "force-stop", packageName).isSuccess

    /** Uninstalls a user-installed app without the per-app system dialog. */
    suspend fun uninstall(packageName: String): Boolean =
        shell.run("pm", "uninstall", packageName).succeededWith("Success")

    /**
     * Removes a pre-installed app for the current user only. The APK stays on the system
     * partition, so [restore] brings it back and a factory reset restores it too.
     */
    suspend fun removeForUser(packageName: String): Boolean =
        shell.run("pm", "uninstall", "-k", "--user", CURRENT_USER, packageName).succeededWith("Success")

    suspend fun restore(packageName: String): Boolean =
        shell.run("cmd", "package", "install-existing", "--user", CURRENT_USER, packageName).let {
            it.isSuccess && "installed for user" in it.output
        }

    suspend fun disable(packageName: String): Boolean =
        shell.run("pm", "disable-user", "--user", CURRENT_USER, packageName).let { it.isSuccess && "disabled" in it.output }

    suspend fun enable(packageName: String): Boolean =
        shell.run("pm", "enable", "--user", CURRENT_USER, packageName).let { it.isSuccess && "enabled" in it.output }

    /** Deletes a folder inside Android/data or obb for good. */
    suspend fun deletePrivileged(path: String): Boolean = shell.delete(path)

    /** True when a per-app clean also reaches the private cache (root mode). */
    val canClearInternalCache: Boolean get() = shell.canClearInternalCache

    /**
     * Empties Android/data/<pkg>/cache and, with root, the app's private cache folders too.
     * Returns bytes freed.
     */
    suspend fun clearAppCache(packageName: String): Long {
        val external = shell.deleteContents(PrivilegedPaths.externalCacheOf(packageName))
        if (!shell.canClearInternalCache) return external
        return external + PrivilegedPaths.internalCachesOf(packageName).sumOf { shell.deleteContents(it) }
    }

    /**
     * Asks the system to free every app's cache (internal and external), the same thing
     * Android does when storage runs low. `pm clear --cache-only` hangs on some One UI
     * builds, so the system-wide trim is used instead of per-app calls.
     */
    suspend fun trimAllCaches(): Boolean =
        shell.run("pm", "trim-caches", TRIM_EVERYTHING, timeoutMillis = TRIM_TIMEOUT).isSuccess

    private fun ShellResult.succeededWith(marker: String) = isSuccess && marker in output

    private companion object {
        const val CURRENT_USER = "current"

        /** "Make this much space free" – larger than any phone, so every cache goes. */
        const val TRIM_EVERYTHING = "1000T"
        const val TRIM_TIMEOUT = 120_000L
    }
}
