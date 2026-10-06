package vn.sysclean.core.data.source

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.model.AppInfo
import vn.sysclean.core.model.AppSize
import vn.sysclean.core.model.PreinstalledApp
import vn.sysclean.core.privilege.AccessRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AppsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val accessRepository: AccessRepository,
) : AppsRepository {

    private val packageManager = context.packageManager
    private val statsManager = context.getSystemService(StorageStatsManager::class.java)
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    private val mutex = Mutex()
    private var cache: List<AppInfo>? = null
    private var cachedAt = 0L
    private var cachedWithUsageAccess = false

    override suspend fun installedApps(forceRefresh: Boolean): List<AppInfo> = mutex.withLock {
        val usageAccess = accessRepository.state.value.usageAccess
        val fresh = System.currentTimeMillis() - cachedAt < CACHE_TTL_MILLIS
        // A newly granted usage access makes the cached copy stale even if it is recent.
        cache?.takeIf { !forceRefresh && fresh && cachedWithUsageAccess == usageAccess }?.let { return it }

        load(usageAccess).also {
            cache = it
            cachedAt = System.currentTimeMillis()
            cachedWithUsageAccess = usageAccess
        }
    }

    override fun isInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override suspend fun preinstalled(packageNames: Set<String>): List<PreinstalledApp> = withContext(io) {
        packageNames.mapNotNull { name ->
            val info = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(
                        name,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(name, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            val enabledSetting = runCatching { packageManager.getApplicationEnabledSetting(name) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            PreinstalledApp(
                packageName = name,
                label = info.loadLabel(packageManager).toString(),
                installedForUser = info.flags and ApplicationInfo.FLAG_INSTALLED != 0,
                disabledByUser = enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            )
        }.sortedBy { it.label.lowercase() }
    }

    override fun defaultLauncher(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" } // "android" = chooser: no default picked
    }

    /**
     * Apps doing a job the user set up and would silently lose if the app were stopped:
     * home screen, keyboard, accessibility, notification access, device admin, default SMS
     * and phone apps, always-on VPN. Plus this app and Shizuku.
     */
    override fun packagesInActiveUse(): Set<String> = buildSet {
        val resolver = context.contentResolver
        defaultLauncher()?.let(::add)
        Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')?.let(::add)
        listOf("enabled_accessibility_services", "enabled_notification_listeners").forEach { key ->
            runCatching { Settings.Secure.getString(resolver, key) }.getOrNull()
                ?.split(':')?.mapNotNull { it.substringBefore('/').takeIf(String::isNotBlank) }?.let(::addAll)
        }
        runCatching { Settings.Secure.getString(resolver, "always_on_vpn_app") }.getOrNull()?.takeIf { it.isNotBlank() }?.let(::add)
        runCatching {
            context.getSystemService(DevicePolicyManager::class.java).activeAdmins.orEmpty().map { it.packageName }
        }.getOrNull()?.let(::addAll)
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let(::add)
        runCatching { context.getSystemService(TelecomManager::class.java).defaultDialerPackage }.getOrNull()?.let(::add)
        add(context.packageName)
        add("moe.shizuku.privileged.api")
    }

    override fun launchablePackages(): Set<String> =
        packagesHandling(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))

    override suspend fun unusedPreinstalled(idleDays: Int): Set<String> = withContext(io) {
        if (!accessRepository.state.value.usageAccess) return@withContext emptySet()
        val now = System.currentTimeMillis()
        val idleMillis = TimeUnit.DAYS.toMillis(idleDays.toLong())
        val stats = usageStatsManager.queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(365), now)
        val launchable = launchablePackages()
        val excluded = essentialPackages() + packagesInActiveUse()
        installedApps()
            .asSequence()
            .filter { it.isSystem && it.isEnabled && it.packageName in launchable && it.packageName !in excluded }
            .filter { now - (it.lastUsedAt ?: 0) > idleMillis }
            .filterNot { app ->
                // Working for the user without being opened: a watch, a car, a step counter…
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    (stats[app.packageName]?.lastTimeForegroundServiceUsed ?: 0) > now - idleMillis
            }
            .map { it.packageName }
            .toSet()
    }

    override suspend fun disabledPreinstalled(): List<PreinstalledApp> = withContext(io) {
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
        }
        val disabled = apps
            .filter { it.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 && !it.enabled }
            .map { it.packageName }
            .toSet()
        preinstalled(disabled).filter { it.disabledByUser }
    }

    /**
     * Apps that do a job the user relies on without opening them, found by what they handle
     * rather than by name: the same on Samsung, Xiaomi, OPPO, vivo, Pixel or anything else.
     */
    private fun essentialPackages(): Set<String> = buildSet {
        listOf(
            Intent(AlarmClock.ACTION_SET_ALARM),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI),
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI),
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(Intent.ACTION_VIEW).setType("image/*"),
            Intent(Intent.ACTION_VIEW).setType("video/*"),
            Intent(Intent.ACTION_GET_CONTENT).setType("*/*"),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.packageName)),
            Intent(Intent.ACTION_ASSIST),
            Intent(Settings.ACTION_SETTINGS),
        ).forEach { addAll(packagesHandling(it)) }
        // App stores: turning one off would stop updates of everything it installed.
        installedPackages().mapNotNullTo(this) { installerOf(it.packageName) }
    }

    private fun packagesHandling(intent: Intent): Set<String> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.mapTo(mutableSetOf()) { it.activityInfo.packageName }
    }.getOrDefault(emptySet())

    private fun installerOf(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun load(usageAccess: Boolean): List<AppInfo> = withContext(io) {
        val packages = installedPackages()
        val lastUsed = if (usageAccess) lastUsedTimes() else emptyMap()
        val sizeDispatcher = io.limitedParallelism(8)
        coroutineScope {
            packages.map { pkg ->
                async(sizeDispatcher) {
                    val app = pkg.applicationInfo ?: return@async null
                    AppInfo(
                        packageName = pkg.packageName,
                        label = app.loadLabel(packageManager).toString(),
                        versionName = pkg.versionName,
                        isSystem = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                        isEnabled = app.enabled,
                        targetSdk = app.targetSdkVersion,
                        installedAt = pkg.firstInstallTime,
                        updatedAt = pkg.lastUpdateTime,
                        lastUsedAt = lastUsed[pkg.packageName],
                        size = if (usageAccess) sizeOf(app) else null,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun installedPackages(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }

    /** The system keeps a yearly usage bucket, so one query covers "not used in months". */
    private fun lastUsedTimes(): Map<String, Long> {
        val now = System.currentTimeMillis()
        return usageStatsManager
            .queryAndAggregateUsageStats(now - TimeUnit.DAYS.toMillis(365), now)
            .mapValues { it.value.lastTimeUsed }
            .filterValues { it > 0 }
    }

    private fun sizeOf(app: ApplicationInfo): AppSize? = try {
        val stats = statsManager.queryStatsForPackage(app.storageUuid, app.packageName, Process.myUserHandle())
        AppSize(apkBytes = stats.appBytes, dataBytes = stats.dataBytes, cacheBytes = stats.cacheBytes)
    } catch (_: Exception) {
        // Packages can vanish mid-query (uninstall) or live on an ejected volume.
        null
    }

    private companion object {
        val CACHE_TTL_MILLIS = TimeUnit.SECONDS.toMillis(30)
    }
}
