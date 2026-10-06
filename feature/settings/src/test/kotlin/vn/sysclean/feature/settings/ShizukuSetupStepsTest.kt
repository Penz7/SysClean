package vn.sysclean.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.sysclean.core.model.ShizukuSetupStatus
import vn.sysclean.core.model.ShizukuState

class ShizukuSetupStepsTest {
    private val nothing = ShizukuSetupStatus(developerOptions = false, wirelessDebugging = false, wifiConnected = false, usbDebugging = false)
    private val allOn = ShizukuSetupStatus(developerOptions = true, wirelessDebugging = true, wifiConnected = true, usbDebugging = true)

    @Test
    fun freshPhoneStartsAtInstall() {
        val progress = ShizukuSetupSteps.progress(ShizukuState.NOT_INSTALLED, nothing, wirelessDebuggingAvailable = true)
        assertEquals(SetupStep.INSTALL, progress.current)
        assertFalse(progress.finished)
    }

    @Test
    fun stepsAreTickedInOrderFromWhatThePhoneReports() {
        val devOn = nothing.copy(developerOptions = true)
        assertEquals(SetupStep.WIFI, ShizukuSetupSteps.progress(ShizukuState.NOT_RUNNING, devOn, true).current)
        val wifi = devOn.copy(wifiConnected = true)
        assertEquals(SetupStep.WIRELESS_DEBUGGING, ShizukuSetupSteps.progress(ShizukuState.NOT_RUNNING, wifi, true).current)
        assertEquals(SetupStep.PAIR_AND_START, ShizukuSetupSteps.progress(ShizukuState.NOT_RUNNING, allOn, true).current)
        assertEquals(SetupStep.ALLOW, ShizukuSetupSteps.progress(ShizukuState.PERMISSION_REQUIRED, allOn, true).current)
        assertTrue(ShizukuSetupSteps.progress(ShizukuState.READY, allOn, true).finished)
    }

    @Test
    fun anUnreadableSwitchDoesNotBlockTheNextStep() {
        val unknown = allOn.copy(wirelessDebugging = null)
        val progress = ShizukuSetupSteps.progress(ShizukuState.NOT_RUNNING, unknown, true)
        assertEquals(SetupStep.PAIR_AND_START, progress.current)
        assertNull(progress.steps.first { it.step == SetupStep.WIRELESS_DEBUGGING }.done)
    }

    @Test
    fun runningShizukuMeansEarlierStepsWereDoneEvenIfSwitchedOffSince() {
        // Wireless debugging often turns itself off when Wi-Fi changes; Shizuku keeps running.
        val progress = ShizukuSetupSteps.progress(ShizukuState.READY, nothing, true)
        assertTrue(progress.finished)
        assertTrue(progress.steps.all { it.done == true })
    }

    @Test
    fun android10AndOlderUseTheComputerPath() {
        val progress = ShizukuSetupSteps.progress(ShizukuState.NOT_RUNNING, nothing.copy(developerOptions = true), false)
        assertEquals(
            listOf(SetupStep.INSTALL, SetupStep.DEVELOPER_OPTIONS, SetupStep.USB_DEBUGGING, SetupStep.START_WITH_COMPUTER, SetupStep.ALLOW),
            progress.steps.map { it.step },
        )
        assertEquals(SetupStep.USB_DEBUGGING, progress.current)
    }
}
