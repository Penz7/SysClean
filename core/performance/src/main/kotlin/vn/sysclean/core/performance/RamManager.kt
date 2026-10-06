package vn.sysclean.core.performance

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.data.repository.HealthRepository
import vn.sysclean.core.privilege.shell.PrivilegedShell
import vn.sysclean.core.privilege.shell.SystemActions
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lowers the RAM that is taken all the time, instead of killing apps (which Android simply
 * restarts): user apps can be put into deep sleep, optional services disabled. Both undoable.
 */
@Singleton
class RamManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val shell: PrivilegedShell,
    private val systemActions: SystemActions,
    private val appsRepository: AppsRepository,
    private val healthRepository: HealthRepository,
) {
    suspend fun memoryMap(): MemoryMap? =
        Parsers.meminfo(shell.run("dumpsys", "meminfo", timeoutMillis = 60_000).output)

    suspend fun overview(): RamOverview? = withContext(io) {
        val map = memoryMap() ?: return@withContext null
        val installed = appsRepository.installedApps().associateBy { it.packageName }
        val inUse = appsRepository.packagesInActiveUse()
        val unusedPreinstalled = appsRepository.unusedPreinstalled()
        val byPackage = map.processes.filter { it.packageName != null && it.name != "system" }.groupBy { it.packageName!! }
        val disabledByUser = appsRepository.preinstalled(byPackage.keys.filter { it in ResidentServices.optional }.toSet())
            .filter { it.disabledByUser }.map { it.packageName }.toSet()
        val sleeping = deepSleepingPackages()

        val apps = byPackage.map { (pkg, processes) ->
            val app = installed[pkg]
            val kind = AppClassifier.kindOf(pkg, isUserApp = app != null && !app.isSystem, inActiveUse = inUse, unusedPreinstalled)
            AppMemory(
                packageName = pkg,
                label = app?.label ?: pkg,
                pssKb = processes.sumOf { it.pssKb },
                group = AppClassifier.mostActive(processes.map { it.group }),
                kind = kind,
                impact = ResidentServices.optional[pkg],
                deepSleeping = (kind == AppKind.USER_APP || kind == AppKind.IDLE_PRELOAD) && pkg in sleeping,
                disabled = pkg in disabledByUser,
            )
        }.sortedByDescending { it.pssKb }

        RamOverview(
            totalKb = map.totalKb,
            usedKb = map.usedKb,
            freeKb = map.freeKb,
            zramPhysicalKb = map.zramPhysicalKb,
            swapUsedKb = map.swapUsedKb,
            systemKb = (map.usedKb - apps.sumOf { it.pssKb }).coerceAtLeast(0),
            apps = apps,
        )
    }

    /** User apps unopened for two weeks that are still allowed to run in the background. */
    suspend fun deepSleepCandidates(): List<String> = withContext(io) {
        val now = System.currentTimeMillis()
        val sleeping = deepSleepingPackages()
        userApps()
            .filter { now - (it.lastUsedAt ?: 0) > IDLE_MILLIS && now - it.installedAt > IDLE_MILLIS }
            .map { it.packageName }
            .filterNot { it in sleeping }
    }

    /**
     * Everything currently asleep or disabled. Read from the system, not the RAM map: a
     * deep-sleeping app is force-stopped, so it is exactly the one missing from RAM.
     */
    suspend fun reducedApps(): List<ReducedApp> = withContext(io) {
        val sleeping = deepSleepingPackages()
        val inUse = appsRepository.packagesInActiveUse()
        val launchable = appsRepository.launchablePackages()
        // User apps plus pre-installed apps with an icon: the ones a person recognises and
        // may want back. Manufacturers put some background parts to sleep themselves.
        val asleep = appsRepository.installedApps()
            .filter { it.packageName in sleeping && (!it.isSystem || it.packageName in launchable) }
            .filter { AppClassifier.kindOf(it.packageName, !it.isSystem, inUse) != AppKind.LOCKED }
            .map { ReducedApp(it.packageName, it.label, disabled = false) }
        val disabled = appsRepository.disabledPreinstalled()
            .map { ReducedApp(it.packageName, it.label, disabled = true) }
        (asleep + disabled).sortedBy { it.label.lowercase() }
    }

    /** All packages barred from running in the background, in one call. */
    private suspend fun deepSleepingPackages(): Set<String> = runCatching {
        shell.run("cmd", "appops", "query-op", "RUN_ANY_IN_BACKGROUND", "ignore").output
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    private suspend fun userApps() = appsRepository.packagesInActiveUse().let { inUse ->
        appsRepository.installedApps().filter { !it.isSystem && AppClassifier.kindOf(it.packageName, true, inUse) == AppKind.USER_APP }
    }

    /**
     * The app may only run while the user has it open: no background services, jobs or
     * alarms, and it does not restart itself. Opening it from the launcher still works.
     */
    suspend fun deepSleep(packageName: String): Boolean {
        val background = shell.run("cmd", "appops", "set", packageName, "RUN_ANY_IN_BACKGROUND", "ignore").isSuccess
        shell.run("am", "set-standby-bucket", packageName, "restricted")
        shell.run("am", "force-stop", packageName)
        healthRepository.invalidate()
        return background
    }

    suspend fun wake(packageName: String): Boolean {
        val background = shell.run("cmd", "appops", "set", packageName, "RUN_ANY_IN_BACKGROUND", "allow").isSuccess
        shell.run("am", "set-standby-bucket", packageName, "active")
        return background
    }

    suspend fun disableService(packageName: String): Boolean = systemActions.disable(packageName).also {
        if (it) shell.run("am", "force-stop", packageName)
        healthRepository.invalidate()
    }

    suspend fun enableService(packageName: String): Boolean = systemActions.enable(packageName)

    /** Restarting clears what a long uptime accumulates in system_server and zRAM. */
    suspend fun restartDevice(): Boolean = shell.run("svc", "power", "reboot").isSuccess

    private companion object {
        val IDLE_MILLIS = TimeUnit.DAYS.toMillis(14)
    }
}
