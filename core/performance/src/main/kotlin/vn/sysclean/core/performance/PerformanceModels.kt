package vn.sysclean.core.performance

import vn.sysclean.core.model.ThermalStatus

/** Everything measured for the performance screen. Privileged fields are null without Shizuku/root. */
data class PerformanceReport(
    val uptimeMillis: Long,
    val ramTotalBytes: Long,
    val ramAvailableBytes: Long,
    val storageFreeFraction: Float?,
    val animationScale: Float,
    val thermal: ThermalStatus,
    /** Battery saver slows the CPU and screen on every brand. */
    val powerSaveMode: Boolean = false,
    /**
     * Enabled accessibility services. Each one is sent every change on screen, all the
     * time: on modest phones an unneeded one (auto-clickers, overlays) is felt as lag.
     */
    val accessibilityServices: List<String> = emptyList(),
    /** Developer option "Don't keep activities": every app restarts each time it is switched to. */
    val dontKeepActivities: Boolean = false,
    // Shizuku / root only:
    val launcherJank: FrameStats? = null,
    val memoryPressure: Pressure? = null,
    val ioPressure: Pressure? = null,
    val systemServerKb: Long? = null,
    /** Apps used in the last two weeks whose code is not ahead-of-time compiled. */
    val unoptimizedApps: List<String>? = null,
    /** User apps unused for two weeks that may still wake up in the background. */
    val deepSleepCandidates: List<String>? = null,
) {
    val uptimeDays: Int get() = (uptimeMillis / DAY_MILLIS).toInt()
    val shouldRestart: Boolean get() = uptimeMillis >= RESTART_AFTER_MILLIS

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val RESTART_AFTER_MILLIS = 7 * DAY_MILLIS
    }
}

/** A before/after pair kept across app restarts, so the improvement is real, not claimed. */
data class Baseline(
    val measuredAt: Long,
    val jankPercent: Double?,
    val ramAvailableBytes: Long,
)

enum class OptimizeStep { COMPILE_APPS, TRIM_STORAGE, DEEP_SLEEP_IDLE_APPS, FREE_RAM, FASTER_ANIMATIONS }

data class OptimizeProgress(val step: OptimizeStep, val done: Int, val total: Int, val current: String?)

data class OptimizeResult(
    val compiledApps: Int,
    val trimmed: Boolean,
    val sleptApps: Int,
    val freedRam: Boolean,
    val animationsChanged: Boolean,
    val ramBeforeBytes: Long,
    val ramAfterBytes: Long,
)

enum class AppKind {
    /** Core system, launcher, keyboard, this app, Shizuku: never touched. */
    LOCKED,

    /** Installed by the user: can be put to deep sleep. */
    USER_APP,

    /** Pre-installed and known to be optional: can be disabled for this user. */
    OPTIONAL_SERVICE,

    /**
     * Pre-installed app with an icon, unopened for a month and doing no everyday job: can be
     * put to deep sleep like a user app. Found by usage, so it works on any manufacturer.
     */
    IDLE_PRELOAD,

    /** Other pre-installed packages: shown, but left alone. */
    SYSTEM_APP,
}

data class AppMemory(
    val packageName: String,
    val label: String,
    val pssKb: Long,
    /** The most "active" group among the app's processes. */
    val group: MemoryGroup,
    val kind: AppKind,
    val impact: ServiceImpact?,
    val deepSleeping: Boolean,
    val disabled: Boolean,
) {
    /** Deep sleep (rather than disabling) is how this app's RAM is reduced. */
    val sleepable: Boolean get() = kind == AppKind.USER_APP || kind == AppKind.IDLE_PRELOAD
}

data class RamOverview(
    val totalKb: Long,
    val usedKb: Long,
    val freeKb: Long,
    val zramPhysicalKb: Long,
    val swapUsedKb: Long,
    /** Kernel, native daemons and system_server: not attributable to an app. */
    val systemKb: Long,
    val apps: List<AppMemory>,
)

/** An app SysClean (or the system) has put to sleep or disabled; listed whether or not it is in RAM. */
data class ReducedApp(
    val packageName: String,
    val label: String,
    /** True for a disabled service, false for a deep-sleeping app. */
    val disabled: Boolean,
)
