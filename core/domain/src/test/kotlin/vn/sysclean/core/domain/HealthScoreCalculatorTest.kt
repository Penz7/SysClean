package vn.sysclean.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import vn.sysclean.core.model.BatteryHealth
import vn.sysclean.core.model.BatteryInfo
import vn.sysclean.core.model.BatteryStatus
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.PlugType
import vn.sysclean.core.model.StorageInfo
import vn.sysclean.core.model.StorageVolumeInfo
import vn.sysclean.core.model.ThermalStatus

class HealthScoreCalculatorTest {
    private fun storage(total: Long, free: Long) =
        StorageInfo(listOf(StorageVolumeInfo("Internal", true, false, total, free)), breakdown = null)

    private fun memory(total: Long, available: Long) = MemoryInfo(total, available, 0, false, null, null)

    private fun battery(health: BatteryHealth) = BatteryInfo(
        80, BatteryStatus.DISCHARGING, health, PlugType.NONE, 30f, 4000, null, null, null, null, null, ThermalStatus.NONE,
    )

    private fun junk(safeBytes: Long) = safeBytes

    @Test
    fun healthyDeviceScoresFull() {
        val score = HealthScoreCalculator.calculate(storage(100, 50), memory(100, 50), battery(BatteryHealth.GOOD), junk(0))
        assertEquals(100, score!!.score)
    }

    @Test
    fun missingJunkFactorIsRescaledNotPenalised() {
        val score = HealthScoreCalculator.calculate(storage(100, 50), memory(100, 50), battery(BatteryHealth.GOOD), null)!!
        assertEquals(100, score.score)
        assertFalse(HealthFactor.JUNK in score.factors)
    }

    @Test
    fun fullStorageAndLotsOfJunkDragScoreDown() {
        // 0% free -> 0/40, junk 5% of capacity -> 0/20, RAM healthy 20/20, battery good 20/20 => 40/100
        val score = HealthScoreCalculator.calculate(storage(1000, 0), memory(100, 50), battery(BatteryHealth.GOOD), junk(50))
        assertEquals(40, score!!.score)
    }

    @Test
    fun halfwayValuesScaleLinearly() {
        // 12.5% free -> 20/40, 15% RAM available -> 10/20, overheating battery -> 5/20 => 35/80 = 44
        val score = HealthScoreCalculator.calculate(storage(1000, 125), memory(100, 15), battery(BatteryHealth.OVERHEAT), null)
        assertEquals(44, score!!.score)
    }

    @Test
    fun potentialGainIsExpressedOnTheVisibleScale() {
        // 12.5% free -> 20/40 storage; RAM and battery full; no scan yet -> total possible 80.
        val score = HealthScoreCalculator.calculate(storage(1000, 125), memory(100, 50), battery(BatteryHealth.GOOD), null)!!
        assertEquals(75, score.score)
        assertEquals(25, score.potentialGain(HealthFactor.STORAGE)) // 20 of 80 possible -> 25 points
        assertEquals(0, score.potentialGain(HealthFactor.MEMORY))
        assertEquals(0, score.potentialGain(HealthFactor.JUNK)) // not measured yet
    }

    @Test
    fun nothingMeasuredGivesNoScore() {
        assertNull(HealthScoreCalculator.calculate(null, null, null, null))
    }
}
