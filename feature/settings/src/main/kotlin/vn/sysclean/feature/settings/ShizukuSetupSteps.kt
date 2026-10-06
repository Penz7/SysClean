package vn.sysclean.feature.settings

import vn.sysclean.core.model.ShizukuSetupStatus
import vn.sysclean.core.model.ShizukuState

enum class SetupStep {
    INSTALL,
    DEVELOPER_OPTIONS,
    WIFI,
    WIRELESS_DEBUGGING,
    PAIR_AND_START,

    /** Android 8-10: no wireless debugging, so Shizuku is started from a computer. */
    USB_DEBUGGING,
    START_WITH_COMPUTER,
    ALLOW,
}

/** [done] is null when the phone does not let SysClean check that step. */
data class SetupStepState(val step: SetupStep, val done: Boolean?)

data class SetupProgress(val steps: List<SetupStepState>) {
    /** The first step still to do; unknown steps never block the ones after them. */
    val current: SetupStep? get() = steps.firstOrNull { it.done == false }?.step
    val finished: Boolean get() = current == null && steps.last().done == true
}

/**
 * Turns what the phone reports into the guide's checklist. Pure, so it is unit tested: the
 * guide must never tick a step the user has not done, nor ask for one already done.
 */
object ShizukuSetupSteps {
    fun progress(shizuku: ShizukuState, status: ShizukuSetupStatus, wirelessDebuggingAvailable: Boolean): SetupProgress {
        val installed = shizuku != ShizukuState.NOT_INSTALLED
        // Once Shizuku runs, everything before it must have been done, even if a switch was
        // turned off since (wireless debugging may go off when Wi-Fi changes).
        val running = shizuku == ShizukuState.PERMISSION_REQUIRED || shizuku == ShizukuState.READY
        val allowed = shizuku == ShizukuState.READY
        val before = { done: Boolean? -> if (running) true else done }
        val steps = if (wirelessDebuggingAvailable) {
            listOf(
                SetupStepState(SetupStep.INSTALL, installed),
                SetupStepState(SetupStep.DEVELOPER_OPTIONS, before(status.developerOptions)),
                SetupStepState(SetupStep.WIFI, before(status.wifiConnected)),
                SetupStepState(SetupStep.WIRELESS_DEBUGGING, before(status.wirelessDebugging)),
                SetupStepState(SetupStep.PAIR_AND_START, running),
                SetupStepState(SetupStep.ALLOW, allowed),
            )
        } else {
            listOf(
                SetupStepState(SetupStep.INSTALL, installed),
                SetupStepState(SetupStep.DEVELOPER_OPTIONS, before(status.developerOptions)),
                SetupStepState(SetupStep.USB_DEBUGGING, before(status.usbDebugging)),
                SetupStepState(SetupStep.START_WITH_COMPUTER, running),
                SetupStepState(SetupStep.ALLOW, allowed),
            )
        }
        return SetupProgress(steps)
    }
}
