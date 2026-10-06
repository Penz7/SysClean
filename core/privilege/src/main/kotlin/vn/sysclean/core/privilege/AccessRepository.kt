package vn.sysclean.core.privilege

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import vn.sysclean.core.common.di.ApplicationScope
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.model.AccessState
import vn.sysclean.core.model.ShizukuSetupStatus
import vn.sysclean.core.model.ShizukuState
import vn.sysclean.core.privilege.shell.ProcessRunner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for what the app is allowed to do. Permissions are granted in
 * system Settings, so callers must [refresh] when the user comes back to the app.
 */
@Singleton
class AccessRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val processRunner: ProcessRunner,
) {
    // A plain preference: the data layer depends on this module, so DataStore there is out of reach.
    private val prefs = context.getSharedPreferences("privilege", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AccessState.Unknown)
    val state: StateFlow<AccessState> = _state.asStateFlow()

    private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refresh()
    }

    /**
     * Re-reads every permission off the main thread: root detection alone stats a dozen paths
     * and queries several packages, which visibly delayed the first frame when done on resume.
     */
    fun refresh() {
        scope.launch(io) { refreshNow() }
    }

    /** Synchronous variant for callers already off the main thread that need the fresh state. */
    fun refreshNow() {
        val shizuku = shizukuState()
        if (shizuku == ShizukuState.READY) prefs.edit().putBoolean(KEY_SHIZUKU_SET_UP, true).apply()
        _state.value = AccessState(
            allFilesAccess = hasAllFilesAccess(),
            usageAccess = hasUsageAccess(),
            shizuku = shizuku,
            rootDetected = rootIndicators().isNotEmpty(),
            rootMode = prefs.getBoolean(KEY_ROOT_MODE, false),
            shizukuWasSetUp = prefs.getBoolean(KEY_SHIZUKU_SET_UP, false),
        )
    }

    /** "Don't remind me": stops the stopped-Shizuku reminder until Shizuku works again. */
    fun forgetShizukuSetup() {
        prefs.edit().putBoolean(KEY_SHIZUKU_SET_UP, false).apply()
        refresh()
    }

    /** What the setup guide checks off; all plain reads, no permission needed. */
    fun setupStatus(): ShizukuSetupStatus {
        val resolver = context.contentResolver
        return ShizukuSetupStatus(
            developerOptions = Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
            // Hidden setting; readable on the versions tested, guarded for the rest.
            wirelessDebugging = runCatching { Settings.Global.getInt(resolver, ADB_WIFI_ENABLED) == 1 }.getOrNull(),
            wifiConnected = runCatching {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }.getOrDefault(false),
            usbDebugging = Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1,
        )
    }

    /**
     * Developer options scrolled to "Wireless debugging": the extra is what Settings itself
     * uses for search results, and ROMs that ignore it simply open the page at the top.
     */
    fun wirelessDebuggingIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .putExtra(EXTRA_FRAGMENT_ARG_KEY, "toggle_adb_wireless")
        .putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, android.os.Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, "toggle_adb_wireless") })

    /**
     * The only place that calls `su` on its own: when the user taps "Turn on root mode".
     * Magisk/KernelSU show their grant prompt here; we wait for the answer.
     */
    suspend fun enableRootMode(): Boolean = withContext(io) {
        val result = processRunner.run(listOf("su", "-c", "id"), ROOT_PROMPT_TIMEOUT)
        val granted = result.isSuccess && "uid=0" in result.output
        prefs.edit().putBoolean(KEY_ROOT_MODE, granted).apply()
        refreshNow()
        granted
    }

    fun disableRootMode() {
        prefs.edit().putBoolean(KEY_ROOT_MODE, false).apply()
        refresh()
    }

    fun rootIndicators(): List<String> = RootDetector.indicators(context.packageManager)

    /**
     * A manufacturer setting that silently breaks Shizuku on this phone, if any. Everything
     * else about Shizuku is plain Android and the same on every brand.
     */
    val shizukuQuirk: ShizukuQuirk? = ShizukuQuirk.of(Build.MANUFACTURER, Build.BRAND)

    /** Wireless debugging (Shizuku without a computer) exists from Android 11. */
    val hasWirelessDebugging: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Before Android 11 there is no "all files" switch; the legacy storage permission covers it. */
    val needsLegacyStoragePermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    val legacyStoragePermissions: Array<String> =
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun allFilesAccessIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri())
        } else {
            appDetailsIntent()
        }

    fun allFilesAccessFallbackIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } else {
            appDetailsIntent()
        }

    // Passing the package jumps straight to our toggle on Android 10+; some OEM builds reject it.
    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri())

    fun usageAccessFallbackIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun appDetailsIntent(packageName: String = context.packageName): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    /**
     * Android 11+ system dialog that clears the cache of every app at once. Needs
     * "All files access" and must be started for result. Null on older versions.
     */
    fun clearAllAppCacheIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasAllFilesAccess()) {
            Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        } else {
            null
        }

    /** Google Play listing first (auto-updates); the official download page as fallback. */
    fun shizukuInstallIntents(): Pair<Intent, Intent> =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")) to
            Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL))

    fun developerOptionsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    /** Developer options are hidden until the build number is tapped seven times in "About phone". */
    fun aboutPhoneIntent(): Intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)

    fun shizukuLaunchIntent(): Intent? = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)

    /** Asks Shizuku for permission; the result arrives through the listener and refreshes [state]. */
    fun requestShizukuPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            legacyStoragePermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun shizukuState(): ShizukuState {
        if (!context.packageManager.isInstalled(SHIZUKU_PACKAGE)) return ShizukuState.NOT_INSTALLED
        if (!Shizuku.pingBinder()) return ShizukuState.NOT_RUNNING
        if (Shizuku.isPreV11()) return ShizukuState.PERMISSION_REQUIRED
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuState.READY
        } else {
            ShizukuState.PERMISSION_REQUIRED
        }
    }

    private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
        private const val SHIZUKU_REQUEST_CODE = 1001
        private const val KEY_ROOT_MODE = "root_mode"
        private const val KEY_SHIZUKU_SET_UP = "shizuku_set_up"
        private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
        private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"

        /** Long enough for the user to read and answer the superuser prompt. */
        private const val ROOT_PROMPT_TIMEOUT = 60_000L
    }
}

/**
 * Launches [primary], falling back to [fallback] on ROMs that do not handle the specific intent
 * or refuse it with a SecurityException.
 */
fun Context.startActivityWithFallback(primary: Intent, fallback: Intent?) {
    try {
        startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: RuntimeException) {
        fallback?.let {
            try {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: RuntimeException) {
                // Nothing on this ROM can open the screen; the UI keeps showing the missing permission.
            }
        }
    }
}
