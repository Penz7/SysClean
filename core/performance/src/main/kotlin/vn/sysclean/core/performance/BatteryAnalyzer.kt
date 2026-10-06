package vn.sysclean.core.performance

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.privilege.shell.PrivilegedShell
import javax.inject.Inject
import javax.inject.Singleton

data class BatteryApp(
    val packageName: String,
    val label: String,
    val powerMah: Double,
    /** Share of all apps' estimated use, 0-100. */
    val sharePercent: Double,
    val wakeupsPerHour: Double,
    /** Minutes per hour the CPU was kept awake in the background. */
    val awakeMinutesPerHour: Double,
    val kind: AppKind,
    val impact: ServiceImpact?,
    /** Only for apps the user can act on: system parts doing their job are not called out. */
    val signs: Set<DrainSign>,
    val deepSleeping: Boolean,
) {
    val sleepable: Boolean get() = kind == AppKind.USER_APP || kind == AppKind.IDLE_PRELOAD
}

data class BatteryReport(
    val periodMillis: Long,
    val screenOffMillis: Long,
    val capacityMah: Int?,
    val dischargeMah: Double?,
    /** False on builds that do not estimate mAh: the list is then ordered by activity instead. */
    val hasPowerEstimates: Boolean,
    val apps: List<BatteryApp>,
    /** Android itself (screen excluded): shown so app numbers are seen in proportion. */
    val systemMah: Double,
) {
    val reliable: Boolean get() = periodMillis >= BatteryDrain.MIN_RELIABLE_PERIOD_MILLIS
}

/**
 * Which apps use the battery since the last full charge, from Android's own accounting. Needs
 * Shizuku or root; works the same on every manufacturer because the checkin format is AOSP.
 */
@Singleton
class BatteryAnalyzer @Inject constructor(
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val shell: PrivilegedShell,
    private val appsRepository: AppsRepository,
    private val ramManager: RamManager,
) {
    suspend fun report(): BatteryReport? = withContext(io) {
        val result = shell.run("dumpsys", "batterystats", "--checkin", timeoutMillis = DUMP_TIMEOUT)
        if (!result.isSuccess) return@withContext null
        val stats = BatteryStatsParser.parse(result.output) ?: return@withContext null

        val installed = appsRepository.installedApps().associateBy { it.packageName }
        val inUse = appsRepository.packagesInActiveUse()
        val unused = appsRepository.unusedPreinstalled()
        val sleeping = ramManager.deepSleepingPackages()
        val hasPower = stats.uids.any { it.powerMah > 0 }
        val (apps, system) = stats.uids.partition { it.isApp }
        val appTotal = apps.sumOf { BatteryDrain.weight(it, hasPower) }.takeIf { it > 0 } ?: 1.0
        val hours = stats.periodMillis / 3_600_000.0

        val rows = apps
            // Several uids can map to one package (work profile, second user): keep the heaviest.
            .mapNotNull { uid -> uid.packages.firstOrNull { it in installed }?.let { it to uid } }
            .groupBy({ it.first }, { it.second })
            .map { (pkg, uids) -> pkg to uids.maxBy { BatteryDrain.weight(it, hasPower) } }
            .filter { (_, uid) -> BatteryDrain.weight(uid, hasPower) > 0 }
            .map { (pkg, uid) ->
                val app = installed.getValue(pkg)
                val kind = AppClassifier.kindOf(pkg, !app.isSystem, inUse, unused)
                val actionable = kind != AppKind.LOCKED && kind != AppKind.SYSTEM_APP
                val share = BatteryDrain.weight(uid, hasPower) * 100 / appTotal
                BatteryApp(
                    packageName = pkg,
                    label = app.label,
                    powerMah = uid.powerMah,
                    sharePercent = share,
                    wakeupsPerHour = BatteryDrain.wakeupsPerHour(uid, stats.periodMillis),
                    awakeMinutesPerHour = if (hours > 0) uid.backgroundWakelockMs / 60_000.0 / hours else 0.0,
                    kind = kind,
                    impact = ResidentServices.optional[pkg],
                    signs = if (actionable) BatteryDrain.signsWorthShowing(uid, stats.periodMillis, share) else emptySet(),
                    deepSleeping = pkg in sleeping,
                )
            }
            .sortedByDescending { it.sharePercent }

        BatteryReport(
            periodMillis = stats.periodMillis,
            screenOffMillis = stats.screenOffMillis,
            capacityMah = stats.capacityMah,
            dischargeMah = stats.dischargeMah,
            hasPowerEstimates = hasPower,
            apps = rows,
            systemMah = system.sumOf { it.powerMah },
        )
    }

    private companion object {
        const val DUMP_TIMEOUT = 60_000L
    }
}
