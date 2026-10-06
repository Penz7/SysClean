package vn.sysclean.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatsParserTest {
    private val stats = BatteryStatsParser.parse(javaClass.getResource("/batterystats_checkin.txt")!!.readText())!!
    private fun uid(id: Int) = stats.uids.first { it.uid == id }

    @Test
    fun periodCapacityAndDrain() {
        assertEquals(10_800_000L, stats.periodMillis) // 3 h on battery
        assertEquals(8_640_000L, stats.screenOffMillis)
        assertEquals(5000, stats.capacityMah)
        assertEquals(85.0, stats.dischargeMah!!, 0.001)
    }

    @Test
    fun perUidCountersAreReadAndSummed() {
        val chatty = uid(10100)
        assertEquals(listOf("com.example.chatty"), chatty.packages)
        assertEquals(12.5, chatty.powerMah, 0.001)
        assertEquals(42, chatty.wakeups) // two alarms summed
        assertEquals(600_000L, chatty.wakelockMs)
        assertEquals(540_000L, chatty.backgroundWakelockMs)
        assertEquals(120_000L, chatty.cpuMs)
        assertEquals(0L, chatty.foregroundMs)
        assertEquals(9_010_000L, chatty.backgroundMs) // foreground service + background
    }

    @Test
    fun sharedSystemUidKeepsAllItsPackagesAndIsNotAnApp() {
        val system = uid(1000)
        assertEquals(listOf("android", "com.android.settings"), system.packages)
        assertFalse(system.isApp)
        assertTrue(uid(10100).isApp)
        // Second user's copy of an app is still an app.
        assertTrue(uid(1010100).isApp)
    }

    @Test
    fun junkLinesAreIgnoredAndMissingPeriodMeansNoReport() {
        assertTrue(stats.uids.none { it.uid < 0 })
        assertNull(BatteryStatsParser.parse("9,0,i,vers,36\n9,1000,l,cpu,1,2,0"))
        assertNull(BatteryStatsParser.parse(""))
    }

    @Test
    fun signsFlagTheChattyAppAndNotTheOthers() {
        assertEquals(14.0, BatteryDrain.wakeupsPerHour(uid(10100), stats.periodMillis), 0.01)
        assertEquals(
            setOf(DrainSign.WAKES_OFTEN, DrainSign.KEEPS_AWAKE, DrainSign.BUSY_IN_BACKGROUND),
            BatteryDrain.signs(uid(10100), stats.periodMillis),
        )
        // A game used on screen a lot uses power but is not misbehaving.
        assertEquals(emptySet<DrainSign>(), BatteryDrain.signs(uid(10101), stats.periodMillis))
        assertEquals(emptySet<DrainSign>(), BatteryDrain.signs(uid(10102), stats.periodMillis))
        assertEquals(emptySet<DrainSign>(), BatteryDrain.signs(uid(10100), 0))
    }

    @Test
    fun weightFallsBackWhenTheBuildHasNoPowerEstimates() {
        assertEquals(56.2, BatteryDrain.weight(uid(10101), hasPowerEstimates = true), 0.001)
        // 540 s awake + 42 wake-ups x 10 + 120 s CPU.
        assertEquals(1080.0, BatteryDrain.weight(uid(10100), hasPowerEstimates = false), 0.001)
    }

    @Test
    fun aNegligibleShareIsNeverCalledADrain() {
        val chatty = uid(10100)
        assertEquals(emptySet<DrainSign>(), BatteryDrain.signsWorthShowing(chatty, stats.periodMillis, sharePercent = 0.4))
        assertEquals(BatteryDrain.signs(chatty, stats.periodMillis), BatteryDrain.signsWorthShowing(chatty, stats.periodMillis, sharePercent = 18.0))
    }
}
