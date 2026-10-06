package vn.sysclean

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import vn.sysclean.core.ui.R as UiR
import vn.sysclean.feature.apps.R as AppsR
import vn.sysclean.feature.settings.R as SettingsR
import vn.sysclean.feature.dashboard.R as DashboardR
import vn.sysclean.feature.deviceinfo.R as DeviceR

/**
 * Walks every top-level screen and runs a real junk scan on the device, saving a screenshot
 * of each step to the app's external files dir (Android/data/vn.sysclean/files/smoke).
 *
 * Grant "All files access" and "Usage access" first to exercise the full scan:
 *   adb shell appops set --uid vn.sysclean MANAGE_EXTERNAL_STORAGE allow
 *   adb shell appops set vn.sysclean GET_USAGE_STATS allow
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val activity get() = compose.activity
    private fun text(id: Int, vararg args: Any) = activity.getString(id, *args)

    @Test
    fun visitsEveryScreenAndCompletesAScan() {
        val scanNow = text(DashboardR.string.dashboard_scan_start)
        // The scan card is a lazy item below the fold, so wait for the title and scroll to it.
        compose.waitUntilAtLeastOneExists(hasText(text(DashboardR.string.dashboard_health)), 15_000)
        Thread.sleep(2_500)
        screenshot("01_dashboard")

        val scanAgain = text(DashboardR.string.dashboard_scan_again)
        // The button is a lazy item off screen, so probe by scrolling rather than by lookup.
        val start = listOf(scanAgain, scanNow).first { label ->
            runCatching { compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label)) }.isSuccess
        }
        compose.onNodeWithText(start).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(DashboardR.string.dashboard_scan_cancel)), 10_000)
        compose.waitUntil(240_000) {
            compose.onAllNodes(hasText(text(DashboardR.string.dashboard_scan_cancel))).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasText(scanAgain)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text(DashboardR.string.dashboard_scan_safe)))
        screenshot("02_scan_result")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(scanAgain))
        screenshot("03_scan_result_bottom")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text(DashboardR.string.dashboard_tips_title)))
        screenshot("06_health_tips")

        // Look at the photo categories on real photos. Read-only: nothing is selected or cleaned.
        listOf(UiR.string.ui_junk_similar to "04_similar", UiR.string.ui_junk_blurry to "05_blurry").forEach { (label, shot) ->
            val name = text(label)
            val present = runCatching {
                compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
            }.isSuccess
            if (present) {
                compose.onNodeWithText(name).performClick()
                Thread.sleep(2_000) // thumbnails load asynchronously
                screenshot(shot)
                compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
        }

        navigateTo(R.string.nav_device)
        listOf(
            DeviceR.string.device_tab_overview,
            DeviceR.string.device_tab_cpu,
            DeviceR.string.device_tab_memory,
            DeviceR.string.device_tab_battery,
            DeviceR.string.device_tab_display,
            DeviceR.string.device_tab_storage,
            DeviceR.string.device_tab_sensors,
            DeviceR.string.device_tab_cameras,
            DeviceR.string.device_tab_security,
        ).forEachIndexed { index, tab ->
            compose.onNode(hasText(text(tab)) and isTab and hasAnyAncestor(hasScrollAction())).performClick()
            compose.waitForIdle()
            Thread.sleep(1_200)
            screenshot("1${index}_device_${activity.resources.getResourceEntryName(tab).removePrefix("device_tab_")}")
        }

        navigateTo(R.string.nav_apps)
        val appsStartedAt = System.currentTimeMillis()
        // The summary line ("N apps · size · cache size") appears once the list has loaded.
        compose.waitUntilAtLeastOneExists(hasText("cache", substring = true), 30_000)
        Log.i("SmokeTest", "Apps list loaded in ${System.currentTimeMillis() - appsStartedAt} ms")
        Thread.sleep(1_500) // icons decode asynchronously after the list appears
        screenshot("20_apps")

        // Multi-select for uninstalling: enter and leave the mode without removing anything.
        val uninstallPrefix = text(AppsR.string.apps_uninstall_one, "")
        val firstButton = compose.onAllNodes(hasContentDescription(uninstallPrefix, substring = true)).fetchSemanticsNodes().first()
        val firstLabel = firstButton.config[SemanticsProperties.ContentDescription].first().removePrefix(uninstallPrefix)
        compose.onNode(hasText(firstLabel) and hasAnyAncestor(hasScrollToNodeAction())).performTouchInput { longClick() }
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(AppsR.string.apps_close_selection)), 5_000)
        screenshot("21_apps_selection")
        compose.onNode(hasContentDescription(text(AppsR.string.apps_close_selection))).performClick()

        navigateTo(R.string.nav_settings)
        compose.waitForIdle()
        Thread.sleep(800)
        screenshot("30_settings")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text(SettingsR.string.settings_shizuku)))
        screenshot("32_settings_shizuku")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Tiếng Việt"))
        screenshot("31_settings_bottom")
    }

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    /** Bottom-bar items are the only clickable labels outside a scrollable container. */
    private fun navigateTo(label: Int) {
        compose.onNode(hasText(text(label)) and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(activity.getExternalFilesDir(null), "smoke").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
