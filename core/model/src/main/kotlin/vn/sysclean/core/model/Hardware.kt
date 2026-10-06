package vn.sysclean.core.model

data class CpuCore(
    val index: Int,
    val online: Boolean,
    val minFreqKHz: Long?,
    val maxFreqKHz: Long?,
    val currentFreqKHz: Long?,
    val governor: String?,
)

data class CpuInfo(
    val hardwareName: String?,
    val coreCount: Int,
    val cores: List<CpuCore>,
) {
    /** Cores grouped by max frequency, e.g. big.LITTLE clusters, fastest first. */
    val clusters: List<List<CpuCore>>
        get() = cores.groupBy { it.maxFreqKHz }
            .entries
            .sortedByDescending { it.key ?: 0 }
            .map { it.value }
}

data class MemoryInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val lowMemoryThresholdBytes: Long,
    val isLowMemory: Boolean,
    val swapTotalBytes: Long?,
    val swapFreeBytes: Long?,
) {
    val usedBytes: Long get() = totalBytes - availableBytes
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

enum class BatteryStatus { CHARGING, DISCHARGING, NOT_CHARGING, FULL, UNKNOWN }

enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, FAILURE, COLD, UNKNOWN }

enum class PlugType { AC, USB, WIRELESS, DOCK, NONE }

enum class ThermalStatus { NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN }

data class BatteryInfo(
    val levelPercent: Int,
    val status: BatteryStatus,
    val health: BatteryHealth,
    val plugType: PlugType,
    val temperatureCelsius: Float?,
    val voltageMillivolts: Int?,
    val technology: String?,
    val cycleCount: Int?,
    val currentNowMicroAmp: Long?,
    /** Capacity the manufacturer declares for the cell. */
    val designCapacityMah: Double?,
    /** Full-charge capacity extrapolated from the fuel gauge's charge counter. */
    val estimatedCapacityMah: Double?,
    val thermalStatus: ThermalStatus,
)
