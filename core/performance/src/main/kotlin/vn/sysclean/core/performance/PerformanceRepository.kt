package vn.sysclean.core.performance

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.data.repository.StorageRepository
import vn.sysclean.core.model.ThermalStatus
import vn.sysclean.core.privilege.shell.PrivilegedShell
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Measures what actually makes a phone feel slow. Everything a normal app can read is read
 * directly; frame stats, compile state and the RAM map need Shizuku or root.
 */
@Singleton
class PerformanceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val shell: PrivilegedShell,
    private val appsRepository: AppsRepository,
    private val storageRepository: StorageRepository,
    private val ramManager: RamManager,
) {
    val isPrivileged: Boolean get() = shell.isReady

    suspend fun diagnose(): PerformanceReport = withContext(io) {
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val primary = runCatching { storageRepository.storageInfo().primary }.getOrNull()
        val base = PerformanceReport(
            uptimeMillis = SystemClock.elapsedRealtime(),
            ramTotalBytes = memory.totalMem,
            ramAvailableBytes = memory.availMem,
            storageFreeFraction = primary?.takeIf { it.totalBytes > 0 }?.let { it.freeBytes.toFloat() / it.totalBytes },
            animationScale = animationScale(),
            thermal = thermal(),
            powerSaveMode = context.getSystemService(PowerManager::class.java).isPowerSaveMode,
            accessibilityServices = accessibilityServices(),
            dontKeepActivities = runCatching {
                Settings.Global.getInt(context.contentResolver, Settings.Global.ALWAYS_FINISH_ACTIVITIES, 0) == 1
            }.getOrDefault(false),
        )
        if (!shell.isReady) return@withContext base

        val launcher = appsRepository.defaultLauncher()
        val apps = appsRepository.installedApps()
        val recentlyUsed = apps.filter { (it.lastUsedAt ?: 0) > System.currentTimeMillis() - RECENT_MILLIS }.map { it.packageName }.toSet()
        val unoptimized = runCatching { Parsers.unoptimizedPackages(shell.run("dumpsys", "package", "dexopt", timeoutMillis = 60_000).output) }
            .getOrDefault(emptySet())
        val alwaysOnScreen = setOfNotNull(launcher, "com.android.systemui")
        base.copy(
            launcherJank = launcher?.let { runCatching { Parsers.gfxinfo(shell.run("dumpsys", "gfxinfo", it).output) }.getOrNull() },
            memoryPressure = runCatching { Parsers.pressure(shell.run("cat", "/proc/pressure/memory").output) }.getOrNull(),
            ioPressure = runCatching { Parsers.pressure(shell.run("cat", "/proc/pressure/io").output) }.getOrNull(),
            systemServerKb = runCatching { ramManager.memoryMap()?.processes?.firstOrNull { it.name == "system" }?.pssKb }.getOrNull(),
            unoptimizedApps = unoptimized.filter { it in recentlyUsed || it in alwaysOnScreen }.sorted(),
            deepSleepCandidates = ramManager.deepSleepCandidates(),
        )
    }

    private fun accessibilityServices(): List<String> = runCatching {
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.loadLabel(context.packageManager)?.toString() }
            .distinct()
    }.getOrDefault(emptyList())

    private fun animationScale(): Float = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

    private fun thermal(): ThermalStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalStatus.UNKNOWN
        return when (context.getSystemService(PowerManager::class.java).currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
            else -> ThermalStatus.UNKNOWN
        }
    }

    private companion object {
        val RECENT_MILLIS = TimeUnit.DAYS.toMillis(14)
    }
}
