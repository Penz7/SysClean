package vn.sysclean

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.sysclean.feature.cleaner.CLEAN_BUTTON_TAG
import java.io.File
import vn.sysclean.core.ui.R as UiR
import vn.sysclean.feature.apps.R as AppsR
import vn.sysclean.feature.cleaner.R as CleanerR
import vn.sysclean.feature.dashboard.R as DashboardR
import vn.sysclean.feature.settings.R as SettingsR

/**
 * Shizuku features on a real device. The host prepares fixtures over adb first:
 *   Android/data/com.sysclean.fixture.datagone, Android/obb/com.sysclean.fixture.obbgone,
 *   Android/data/vn.sysclean/cache/probe.bin
 * and checks afterwards that they are gone. Skipped when Shizuku is not ready.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PrivilegedFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Test
    fun deepCleanForceStopAndPreinstalledRoundTrip() {
        assumeShizukuReady()
        scan()

        // Leftovers of uninstalled apps inside Android/data and obb are found and deleted for good.
        openCategory(UiR.string.ui_junk_leftover)
        selectOnly("com.sysclean.fixture.datagone", "com.sysclean.fixture.obbgone")
        compose.onNodeWithTag(CLEAN_BUTTON_TAG).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(CleanerR.string.cleaner_privileged_note)), 5_000)
        screenshot("50_privileged_confirm")
        compose.onNodeWithText(text(CleanerR.string.cleaner_confirm_action)).performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("com.sysclean.fixture.datagone")).fetchSemanticsNodes().isEmpty()
        }
        goBack()

        // App cache: clear only SysClean's own external cache directly.
        openCategory(UiR.string.ui_junk_app_cache)
        screenshot("51_app_cache_shizuku")
        val ownLabel = compose.activity.applicationInfo.loadLabel(compose.activity.packageManager).toString()
        if (runCatching { scrollTo(ownLabel) }.isSuccess) {
            selectOnly(ownLabel)
            compose.onNodeWithTag(CLEAN_BUTTON_TAG).performClick()
            compose.onNodeWithText(text(CleanerR.string.cleaner_confirm_action)).performClick()
            compose.waitUntil(10_000) { !File(compose.activity.externalCacheDir, "probe.bin").exists() }
        }
        goBack()

        // Force stop through the app menu, on the Shizuku manager UI (its server keeps running).
        navigateTo(R.string.nav_apps)
        compose.onNodeWithText(text(AppsR.string.apps_filter_all)).performClick()
        // Rows are lazy: wait for the list to load, then scroll the Shizuku row into view.
        compose.waitUntilAtLeastOneExists(hasText("cache", substring = true), 30_000)
        scrollTo("Shizuku")
        compose.onNode(hasContentDescription(text(AppsR.string.apps_more, "Shizuku"))).performClick()
        compose.onNodeWithText(text(AppsR.string.apps_force_stop)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(stoppedMessage()), 10_000)

        // Pre-installed apps: remove one recommended app for this user, then restore it.
        compose.onNode(hasContentDescription(text(AppsR.string.apps_bloatware))).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(AppsR.string.bloat_recommended)), 10_000)
        screenshot("52_bloatware")
        val target = "com.samsung.android.kidsinstaller"
        if (runCatching { scrollTo(target) }.isSuccess) {
            val label = labelOf(target)
            clickActionInRowOf(target, text(AppsR.string.bloat_remove))
            compose.waitUntilAtLeastOneExists(hasText(text(AppsR.string.bloat_done_removed, label)), 15_000)
            screenshot("53_bloat_removed")
            scrollTo(target)
            clickActionInRowOf(target, text(AppsR.string.bloat_restore))
            compose.waitUntilAtLeastOneExists(hasText(text(AppsR.string.bloat_done_restored, label)), 15_000)
        }
    }

    private fun assumeShizukuReady() {
        navigateTo(R.string.nav_settings)
        val shizuku = text(SettingsR.string.settings_shizuku)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(shizuku))
        val ready = compose.onAllNodes(hasText(text(SettingsR.string.settings_shizuku_ready))).fetchSemanticsNodes().isNotEmpty()
        assumeTrue("Shizuku must be running and granted", ready)
        navigateTo(R.string.nav_dashboard)
    }

    private fun scan() {
        val scanNow = text(DashboardR.string.dashboard_scan_start)
        val scanAgain = text(DashboardR.string.dashboard_scan_again)
        val cancel = text(DashboardR.string.dashboard_scan_cancel)
        compose.waitUntilAtLeastOneExists(hasText(text(DashboardR.string.dashboard_health)), 15_000)
        val start = listOf(scanAgain, scanNow).first { runCatching { scrollTo(it) }.isSuccess }
        compose.onNodeWithText(start).performClick()
        compose.waitUntilAtLeastOneExists(hasText(cancel), 10_000)
        compose.waitUntil(240_000) {
            compose.onAllNodes(hasText(cancel)).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasText(scanAgain)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openCategory(label: Int) {
        scrollTo(text(label))
        compose.onNodeWithText(text(label)).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag(CLEAN_BUTTON_TAG), 10_000)
    }

    private fun selectOnly(vararg names: String) {
        val all = text(CleanerR.string.cleaner_select_all)
        val none = text(CleanerR.string.cleaner_select_none)
        // The header is a lazy item; bring it back on screen before using its buttons.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(all) or hasText(none))
        if (compose.onAllNodes(hasText(all)).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText(all).performClick()
        compose.onNodeWithText(none).performClick()
        names.forEach { name ->
            scrollTo(name)
            compose.onNodeWithText(name).performSemanticsAction(SemanticsActions.OnClick)
        }
    }

    /** Bloat rows are not merged, so find the action button that sits in the row of [packageName]. */
    private fun clickActionInRowOf(packageName: String, action: String) {
        val packageNode = compose.onNodeWithText(packageName).fetchSemanticsNode()
        val buttons = compose.onAllNodes(hasText(action) and hasClickAction()).fetchSemanticsNodes()
        val nearest = buttons.filter { it.boundsInRoot.top >= packageNode.boundsInRoot.top }.minBy { it.boundsInRoot.top }
        // Semantics click: a snackbar can sit on top of the button, which would swallow a tap.
        compose.onAllNodes(hasText(action) and hasClickAction())[buttons.indexOf(nearest)]
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun labelOf(packageName: String): String =
        compose.activity.packageManager.getApplicationInfo(packageName, 0).loadLabel(compose.activity.packageManager).toString()

    private fun stoppedMessage() =
        compose.activity.resources.getQuantityString(AppsR.plurals.apps_force_stopped, 1, 1)

    private fun scrollTo(label: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
    }

    private fun navigateTo(label: Int) {
        compose.onNode(hasText(text(label)) and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()
        compose.waitForIdle()
    }

    private fun goBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(compose.activity.getExternalFilesDir(null), "smoke").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
