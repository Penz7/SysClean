package vn.sysclean.core.domain

import vn.sysclean.core.model.BatteryHealth
import vn.sysclean.core.model.BatteryInfo
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.StorageInfo

enum class HealthFactor { STORAGE, JUNK, MEMORY, BATTERY }

data class HealthScore(
    val score: Int,
    /** Points earned out of the points possible, per factor that could be measured. */
    val factors: Map<HealthFactor, Pair<Int, Int>>,
) {
    /** How many points of the 0..100 score fixing [factor] completely would add. */
    fun potentialGain(factor: HealthFactor): Int {
        val (earned, possible) = factors[factor] ?: return 0
        val total = factors.values.sumOf { it.second }
        return Math.round((possible - earned) * 100.0 / total).toInt()
    }
}

/**
 * Weighted score out of 100. Factors that cannot be measured yet (e.g. junk before the first
 * scan) are left out and the rest is rescaled, so the score is never penalised for missing data.
 */
object HealthScoreCalculator {
    private const val STORAGE_WEIGHT = 40
    private const val JUNK_WEIGHT = 20
    private const val MEMORY_WEIGHT = 20
    private const val BATTERY_WEIGHT = 20

    /** At or above this much free space storage is considered healthy. */
    const val HEALTHY_FREE_RATIO = 0.25

    /** Safe junk at this fraction of capacity scores zero on the junk factor. */
    private const val JUNK_CEILING_RATIO = 0.05
    private const val HEALTHY_AVAILABLE_RAM_RATIO = 0.30

    /**
     * [safeJunkBytes] is the safe-to-clean total of the last scan, or null if never scanned.
     * Kept as a number so the home-screen widget can score without a full report in memory.
     */
    fun calculate(
        storage: StorageInfo?,
        memory: MemoryInfo?,
        battery: BatteryInfo?,
        safeJunkBytes: Long?,
    ): HealthScore? {
        val factors = buildMap {
            val primary = storage?.primary
            if (primary != null && primary.totalBytes > 0) {
                val freeRatio = primary.freeBytes.toDouble() / primary.totalBytes
                put(HealthFactor.STORAGE, points(freeRatio / HEALTHY_FREE_RATIO, STORAGE_WEIGHT) to STORAGE_WEIGHT)
                if (safeJunkBytes != null) {
                    val junkRatio = safeJunkBytes.toDouble() / primary.totalBytes
                    put(HealthFactor.JUNK, points(1 - junkRatio / JUNK_CEILING_RATIO, JUNK_WEIGHT) to JUNK_WEIGHT)
                }
            }
            if (memory != null && memory.totalBytes > 0) {
                val availableRatio = memory.availableBytes.toDouble() / memory.totalBytes
                put(HealthFactor.MEMORY, points(availableRatio / HEALTHY_AVAILABLE_RAM_RATIO, MEMORY_WEIGHT) to MEMORY_WEIGHT)
            }
            if (battery != null) {
                val ratio = when (battery.health) {
                    BatteryHealth.GOOD -> 1.0
                    BatteryHealth.UNKNOWN -> 0.75
                    else -> 0.25
                }
                put(HealthFactor.BATTERY, points(ratio, BATTERY_WEIGHT) to BATTERY_WEIGHT)
            }
        }
        if (factors.isEmpty()) return null
        val earned = factors.values.sumOf { it.first }
        val possible = factors.values.sumOf { it.second }
        return HealthScore(score = Math.round(earned * 100.0 / possible).toInt(), factors = factors)
    }

    private fun points(ratio: Double, weight: Int): Int = Math.round(ratio.coerceIn(0.0, 1.0) * weight).toInt()
}
