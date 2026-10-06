package vn.sysclean.core.performance

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.data.repository.HealthRepository
import vn.sysclean.core.privilege.shell.PrivilegedShell
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-tap optimization. Each step is something Android itself does eventually (idle-time
 * compilation, weekly TRIM, reclaiming cached apps under pressure) or a reversible setting,
 * run now and only on what needs it. Everything is plain AOSP, so it behaves the same on
 * every manufacturer's Android.
 */
@Singleton
class Optimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val shell: PrivilegedShell,
    private val ramManager: RamManager,
    private val appsRepository: AppsRepository,
    private val store: PerformanceStore,
    private val healthRepository: HealthRepository,
) {
    suspend fun run(
        steps: Set<OptimizeStep>,
        report: PerformanceReport,
        onProgress: (OptimizeProgress) -> Unit,
    ): OptimizeResult = withContext(io) {
        val ramBefore = availableRam()
        // Remember "before" so the screen can later show a measured difference.
        store.saveBaseline(Baseline(System.currentTimeMillis(), report.launcherJank?.jankyPercent, ramBefore))

        var compiled = 0
        if (OptimizeStep.COMPILE_APPS in steps) {
            val apps = report.unoptimizedApps.orEmpty()
            apps.forEachIndexed { index, pkg ->
                ensureActive()
                onProgress(OptimizeProgress(OptimizeStep.COMPILE_APPS, index, apps.size, pkg))
                val result = shell.run("cmd", "package", "compile", "-m", "speed-profile", pkg, timeoutMillis = COMPILE_TIMEOUT)
                if (result.isSuccess && "Success" in result.output) compiled++
            }
        }

        var trimmed = false
        if (OptimizeStep.TRIM_STORAGE in steps) {
            onProgress(OptimizeProgress(OptimizeStep.TRIM_STORAGE, 0, 1, null))
            trimmed = shell.run("sm", "fstrim", timeoutMillis = TRIM_TIMEOUT).isSuccess
        }

        var slept = 0
        if (OptimizeStep.DEEP_SLEEP_IDLE_APPS in steps) {
            val apps = report.deepSleepCandidates.orEmpty()
            apps.forEachIndexed { index, pkg ->
                ensureActive()
                onProgress(OptimizeProgress(OptimizeStep.DEEP_SLEEP_IDLE_APPS, index, apps.size, pkg))
                if (ramManager.deepSleep(pkg)) slept++
            }
        }

        // Last, so the "after" figure is not eaten again by compilation or TRIM.
        var freedRam = false
        if (OptimizeStep.FREE_RAM in steps) {
            onProgress(OptimizeProgress(OptimizeStep.FREE_RAM, 0, 1, null))
            // Ends only cached processes: apps already closed that Android keeps around in
            // case they are reopened. Nothing running or visible is stopped and no data is lost.
            freedRam = shell.run("am", "kill-all").isSuccess
        }

        var animations = false
        if (OptimizeStep.FASTER_ANIMATIONS in steps) {
            onProgress(OptimizeProgress(OptimizeStep.FASTER_ANIMATIONS, 0, 1, null))
            animations = setAnimationScale(FAST_ANIMATIONS)
        }

        // Start a fresh frame count so the next measurement reflects the optimized state only.
        appsRepository.defaultLauncher()?.let { shell.run("dumpsys", "gfxinfo", it, "reset") }
        // The kernel returns freed pages over a few seconds; reading at once under-reports.
        delay(SETTLE_MILLIS)
        healthRepository.invalidate()
        OptimizeResult(compiled, trimmed, slept, freedRam, animations, ramBefore, availableRam())
    }

    suspend fun setAnimationScale(scale: Float): Boolean = ANIMATION_KEYS.all { key ->
        shell.run("cmd", "settings", "put", "global", key, scale.toString()).isSuccess
    }

    private fun availableRam(): Long =
        ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.availMem

    companion object {
        const val FAST_ANIMATIONS = 0.5f
        const val NORMAL_ANIMATIONS = 1f
        private val ANIMATION_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        private const val COMPILE_TIMEOUT = 180_000L
        private const val TRIM_TIMEOUT = 300_000L
        private const val SETTLE_MILLIS = 3_000L
    }
}
