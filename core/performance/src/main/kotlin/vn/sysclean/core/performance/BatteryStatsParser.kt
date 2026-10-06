package vn.sysclean.core.performance

/** One Linux uid's share of the battery since the last full charge, as Android estimated it. */
data class UidBattery(
    val uid: Int,
    val packages: List<String>,
    /** Android's own estimate; 0 on builds that do not compute it. */
    val powerMah: Double,
    /** Alarms that woke the CPU from sleep. */
    val wakeups: Int,
    /** Partial wake locks, all and while in the background: the CPU kept awake with the screen off. */
    val wakelockMs: Long,
    val backgroundWakelockMs: Long,
    val cpuMs: Long,
    /** On screen. */
    val foregroundMs: Long,
    /** Foreground service or background process: running while the user is not looking. */
    val backgroundMs: Long,
) {
    /** Uids below 10000 are Android itself (system_server, radio, media...), shared by every app. */
    val isApp: Boolean get() = uid % PER_USER_RANGE >= FIRST_APPLICATION_UID

    private companion object {
        const val PER_USER_RANGE = 100_000
        const val FIRST_APPLICATION_UID = 10_000
    }
}

data class BatteryStats(
    /** Time on battery since the last full charge: everything else is measured over it. */
    val periodMillis: Long,
    val screenOffMillis: Long,
    val capacityMah: Int?,
    val dischargeMah: Double?,
    val uids: List<UidBattery>,
)

/**
 * Reads `dumpsys batterystats --checkin`: the comma-separated format Android keeps stable for
 * machines (bug reports, Battery Historian), unlike the human text that changes every release.
 * Only "since last charged" rows ("l") are used. Unknown rows and short rows are skipped, so a
 * vendor adding fields does not break it.
 */
object BatteryStatsParser {

    fun parse(text: String): BatteryStats? {
        var period: Long? = null
        var screenOff = 0L
        var capacity: Int? = null
        var discharge: Double? = null
        val packages = mutableMapOf<Int, MutableList<String>>()
        val power = mutableMapOf<Int, Double>()
        val wakeups = mutableMapOf<Int, Int>()
        val wakelocks = mutableMapOf<Int, Pair<Long, Long>>()
        val cpu = mutableMapOf<Int, Long>()
        val states = mutableMapOf<Int, Pair<Long, Long>>()

        text.lineSequence().forEach { line ->
            val f = line.split(',')
            if (f.size < 5) return@forEach
            val uid = f[1].toIntOrNull() ?: return@forEach
            when {
                f[2] == "i" && f[3] == "uid" && f.size >= 6 ->
                    f[4].toIntOrNull()?.let { packages.getOrPut(it) { mutableListOf() }.add(f[5]) }
                f[2] != "l" -> Unit
                // bt: start count, battery realtime, battery uptime, total realtime, total uptime,
                // start clock time, battery screen-off realtime, ...
                f[3] == "bt" -> {
                    period = f.long(5)
                    screenOff = f.long(10) ?: 0
                }
                // pws: capacity, computed drain, min drain, max drain.
                f[3] == "pws" -> {
                    capacity = f.getOrNull(4)?.toDoubleOrNull()?.toInt()
                    discharge = f.getOrNull(5)?.toDoubleOrNull()
                }
                f[3] == "pwi" && f[4] == "uid" -> f.getOrNull(5)?.toDoubleOrNull()?.let { power[uid] = (power[uid] ?: 0.0) + it }
                // wua: alarm name, count. One row per alarm; summed per uid.
                f[3] == "wua" -> f.last().toIntOrNull()?.let { wakeups[uid] = (wakeups[uid] ?: 0) + it }
                // awl: partial wake lock time, background partial wake lock time (ms).
                f[3] == "awl" -> wakelocks[uid] = (f.long(4) ?: 0) to (f.long(5) ?: 0)
                // cpu: user ms, system ms.
                f[3] == "cpu" -> cpu[uid] = (f.long(4) ?: 0) + (f.long(5) ?: 0)
                // st: top, foreground service, foreground, background, top sleeping, heavy, cached (ms).
                f[3] == "st" -> states[uid] = (f.long(4) ?: 0) to ((f.long(5) ?: 0) + (f.long(7) ?: 0))
            }
        }

        val total = period ?: return null
        val uids = (power.keys + wakeups.keys + wakelocks.keys + cpu.keys + states.keys).map { uid ->
            UidBattery(
                uid = uid,
                packages = packages[uid].orEmpty(),
                powerMah = power[uid] ?: 0.0,
                wakeups = wakeups[uid] ?: 0,
                wakelockMs = wakelocks[uid]?.first ?: 0,
                backgroundWakelockMs = wakelocks[uid]?.second ?: 0,
                cpuMs = cpu[uid] ?: 0,
                foregroundMs = states[uid]?.first ?: 0,
                backgroundMs = states[uid]?.second ?: 0,
            )
        }
        return BatteryStats(total, screenOff, capacity, discharge, uids)
    }

    private fun List<String>.long(index: Int): Long? = getOrNull(index)?.toLongOrNull()
}

/** Why an app is called out, in terms a user can check against how they use the phone. */
enum class DrainSign {
    /** Wakes the phone from sleep again and again. */
    WAKES_OFTEN,

    /** Keeps the CPU awake while the screen is off. */
    KEEPS_AWAKE,

    /** Runs in the background most of the time while hardly ever opened. */
    BUSY_IN_BACKGROUND,
}

/** Thresholds turning raw counters into the signs above. Pure, so they are unit tested. */
object BatteryDrain {
    private const val HOUR = 3_600_000.0

    /** Less than this on battery and the numbers say more about chance than habits. */
    const val MIN_RELIABLE_PERIOD_MILLIS = 60 * 60 * 1000L

    /** An app below this share of all apps' use is not called a drain, whatever its counters say. */
    const val MIN_SHARE_PERCENT = 1.0

    /** Signs only for apps that actually account for part of the battery. */
    fun signsWorthShowing(app: UidBattery, periodMillis: Long, sharePercent: Double): Set<DrainSign> =
        if (sharePercent < MIN_SHARE_PERCENT) emptySet() else signs(app, periodMillis)

    fun wakeupsPerHour(app: UidBattery, periodMillis: Long): Double =
        if (periodMillis > 0) app.wakeups * HOUR / periodMillis else 0.0

    fun signs(app: UidBattery, periodMillis: Long): Set<DrainSign> {
        if (periodMillis <= 0) return emptySet()
        return buildSet {
            // One wake-up every 10 minutes or more often, all day.
            if (wakeupsPerHour(app, periodMillis) >= 6) add(DrainSign.WAKES_OFTEN)
            // Awake in the background for 2% of the time: about a minute per hour.
            if (app.backgroundWakelockMs >= periodMillis * 0.02) add(DrainSign.KEEPS_AWAKE)
            if (app.backgroundMs >= periodMillis * 0.3 && app.foregroundMs < periodMillis * 0.05) add(DrainSign.BUSY_IN_BACKGROUND)
        }
    }

    /**
     * Sort key: Android's mAh estimate when the build provides one, otherwise a blend of what
     * the estimate is made of, so the order still makes sense on builds without it.
     */
    fun weight(app: UidBattery, hasPowerEstimates: Boolean): Double =
        if (hasPowerEstimates) app.powerMah
        else app.backgroundWakelockMs / 1000.0 + app.wakeups * 10.0 + app.cpuMs / 1000.0
}
