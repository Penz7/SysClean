package vn.sysclean.core.data.repository

import vn.sysclean.core.model.AppInfo
import vn.sysclean.core.model.PreinstalledApp

interface AppsRepository {
    /** Results are cached briefly because several screens and the scanner ask at once. */
    suspend fun installedApps(forceRefresh: Boolean = false): List<AppInfo>

    fun isInstalled(packageName: String): Boolean

    /** Looks up [packageNames] including ones removed for this user; absent ones are skipped. */
    suspend fun preinstalled(packageNames: Set<String>): List<PreinstalledApp>

    /** Packages that must never be touched right now: the home screen and the keyboard in use. */
    fun packagesInActiveUse(): Set<String>

    /** The home screen app the user currently uses. */
    fun defaultLauncher(): String?

    /** Packages with an icon in the app drawer. */
    fun launchablePackages(): Set<String>

    /**
     * Pre-installed apps with an icon that the user has not opened for [idleDays] days and
     * that do no everyday job without being opened (clock, calendar, mail, camera, contacts,
     * browser, gallery, files, maps, assistant, app stores, anything recently running a
     * foreground service). Based on usage and intents rather than package names, so it finds
     * the same kind of app on every manufacturer's Android. Empty without usage access.
     */
    suspend fun unusedPreinstalled(idleDays: Int = 30): Set<String>

    /** Every pre-installed package currently turned off by the user (or by SysClean for them). */
    suspend fun disabledPreinstalled(): List<PreinstalledApp>
}
