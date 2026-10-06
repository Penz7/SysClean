package vn.sysclean

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import vn.sysclean.feature.cleaner.CLEAN_BUTTON_TAG
import java.io.File
import kotlin.random.Random
import vn.sysclean.core.ui.R as UiR
import vn.sysclean.feature.cleaner.R as CleanerR
import vn.sysclean.feature.dashboard.R as DashboardR
import vn.sysclean.feature.settings.R as SettingsR
import vn.sysclean.feature.trash.R as TrashR

/**
 * End-to-end cleaning on a real device, restricted to fixture files this test creates.
 * Every interaction first deselects everything, so the user's own files are never touched.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CleaningFlowTest {

    /**
     * Fixtures are written before the activity starts (order = 0), so the app is not drawing
     * while the test hammers the file system, and the scan sees every fixture.
     */
    @get:Rule(order = 0)
    val fixtures = object : ExternalResource() {
        override fun before() = createFixtures()
        override fun after() = removeFixtures()
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val root = Environment.getExternalStorageDirectory()
    private val fixtureDir = File(root, "Download/SysCleanFixture")
    private val emptyFixture = File(root, "SysCleanFixtureEmpty")
    private val tempA = File(fixtureDir, "fixture_a.tmp")
    private val tempB = File(fixtureDir, "fixture_b.log")
    private val ignoreMe = File(fixtureDir, "fixture_ignore.tmp")
    private val dupeOriginal = File(fixtureDir, "fixture_dupe_1.bin")
    private val dupeCopy = File(fixtureDir, "fixture_dupe_2.bin")

    /** A "leftover" folder of an app that was never installed; hundreds of files make deletion slow. */
    private val leftover = File(root, "Android/media/com.sysclean.fixture.gone")
    @Suppress("DEPRECATION")
    // Target context, not the activity: reading it must not wait for the main thread.
    private val trashDir
        get() = File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "trash")

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun createFixtures() {
        fixtureDir.mkdirs()
        tempA.writeText("temporary A")
        tempB.writeText("log line")
        ignoreMe.writeText("keep me")
        val body = Random(7).nextBytes(2_000_000)
        dupeOriginal.writeBytes(body)
        dupeOriginal.setLastModified(System.currentTimeMillis() - 60_000)
        dupeCopy.writeBytes(body)
        File(emptyFixture, "inner/deeper").mkdirs()
        File(leftover, "cache").mkdirs()
        repeat(400) { File(leftover, "cache/part_$it.bin").writeBytes(ByteArray(512)) }
    }

    private fun removeFixtures() {
        fixtureDir.deleteRecursively()
        emptyFixture.deleteRecursively()
        leftover.deleteRecursively()
    }

    @Test
    fun cleanUndoRestoreAndIgnoreFixtures() {
        scan()

        // Temp files: clean two fixtures, undo, then clean them again for good.
        openCategory(UiR.string.ui_junk_temp)
        selectOnly(tempA.name, tempB.name)
        cleanSelected()
        compose.waitUntilAtLeastOneExists(hasText(text(CleanerR.string.cleaner_undo)), 10_000)
        screenshot("40_cleaned_snackbar")
        assertFalse(tempA.exists())
        compose.onNodeWithText(text(CleanerR.string.cleaner_undo)).performClick()
        compose.waitUntil(10_000) { tempA.exists() && tempB.exists() }

        selectOnly(tempA.name, tempB.name)
        cleanSelected()
        compose.waitUntil(10_000) { !tempA.exists() && !tempB.exists() }

        // Ignore: the item disappears and shows up under Settings > Ignored items.
        scrollTo(ignoreMe.name)
        compose.onNode(hasContentDescription("${text(CleanerR.string.cleaner_more)} ${ignoreMe.name}")).performClick()
        compose.onNodeWithText(text(CleanerR.string.cleaner_ignore)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(ignoreMe.name)).fetchSemanticsNodes().isEmpty() }
        assertTrue(ignoreMe.exists())
        goBack()

        // Duplicates: only the newer copy goes, after a full-content check.
        openCategory(UiR.string.ui_junk_duplicates)
        screenshot("41_duplicates")
        selectOnly(dupeCopy.name)
        cleanSelected()
        compose.waitUntil(15_000) { !dupeCopy.exists() }
        assertTrue(dupeOriginal.exists())
        goBack()

        // Empty folders are deleted outright, nested ones included.
        openCategory(UiR.string.ui_junk_empty)
        selectOnly(emptyFixture.name)
        cleanSelected()
        compose.waitUntil(10_000) { !emptyFixture.exists() }
        goBack()

        // Leftover folder of an uninstalled app goes to the bin as one item.
        openCategory(UiR.string.ui_junk_leftover)
        selectOnly(leftover.name)
        cleanSelected()
        compose.waitUntil(30_000) { !leftover.exists() }
        goBack()

        // Recycle bin: restore one fixture, delete one for good, then empty the rest.
        compose.onNode(hasContentDescription(text(DashboardR.string.dashboard_trash))).performClick()
        compose.waitUntilAtLeastOneExists(hasText(tempA.name), 10_000)
        screenshot("42_trash")
        compose.onNode(hasContentDescription("${text(TrashR.string.trash_restore)} ${tempA.name}")).performClick()
        compose.waitUntil(10_000) { tempA.exists() }
        compose.onNode(hasContentDescription("${text(TrashR.string.trash_delete)} ${tempB.name}")).performClick()
        // The dialog's confirm button is the only *text* reading "Delete forever".
        compose.onNodeWithText(text(TrashR.string.trash_delete)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(tempB.name)).fetchSemanticsNodes().isEmpty() }

        val remaining = listOf(dupeCopy.name, leftover.name)
        val binHoldsOnlyFixtures = trashDir.listFiles().orEmpty().count { it.name != ".nomedia" } == remaining.size
        if (binHoldsOnlyFixtures) {
            // Emptying must clear the list at once, even while hundreds of files are still being deleted.
            compose.onNode(hasContentDescription(text(TrashR.string.trash_empty_action))).performClick()
            compose.onNodeWithText(text(TrashR.string.trash_empty_action)).performClick()
            compose.waitUntilAtLeastOneExists(hasText(text(TrashR.string.trash_nothing_title)), 2_000)
            screenshot("44_trash_emptied")
            compose.waitUntil(60_000) { trashDir.listFiles().orEmpty().none { it.name != ".nomedia" } }
        } else {
            // The user has their own items in the bin: never empty it, delete only the fixtures.
            remaining.forEach { name ->
                compose.onNode(hasContentDescription("${text(TrashR.string.trash_delete)} $name")).performClick()
                compose.onNodeWithText(text(TrashR.string.trash_delete)).performClick()
                compose.waitUntil(2_000) { compose.onAllNodes(hasText(name)).fetchSemanticsNodes().isEmpty() }
            }
        }
        goBack()

        // Ignored items: the fixture is listed and can be removed again.
        compose.onNode(hasText(text(R.string.nav_settings)) and hasClickAction() and !hasAnyAncestor(hasScrollAction()))
            .performClick()
        scrollTo(text(SettingsR.string.settings_whitelist))
        compose.onNodeWithText(text(SettingsR.string.settings_whitelist)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(ignoreMe.name), 5_000)
        screenshot("43_whitelist")
        compose.onNode(hasContentDescription("${text(SettingsR.string.whitelist_remove)} ${ignoreMe.name}")).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(ignoreMe.name)).fetchSemanticsNodes().isEmpty() }
    }

    /** Starts a fresh scan; the scanner is a process-wide singleton, so a previous test may have run one. */
    private fun scan() {
        val scanNow = text(DashboardR.string.dashboard_scan_start)
        val scanAgain = text(DashboardR.string.dashboard_scan_again)
        val cancel = text(DashboardR.string.dashboard_scan_cancel)
        compose.waitUntilAtLeastOneExists(hasText(text(DashboardR.string.dashboard_health)), 15_000)
        // The button is a lazy item off screen, so probe by scrolling rather than by lookup.
        val start = listOf(scanAgain, scanNow).first { label -> runCatching { scrollTo(label) }.isSuccess }
        compose.onNodeWithText(start).performClick()
        // The scan starts asynchronously; wait for it to begin before waiting for it to end.
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

    /** Clears the default selection (which may include the user's own files), then ticks only [names]. */
    private fun selectOnly(vararg names: String) {
        val all = text(CleanerR.string.cleaner_select_all)
        if (compose.onAllNodes(hasText(all)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(all).performClick()
        }
        compose.onNodeWithText(text(CleanerR.string.cleaner_select_none)).performClick()
        names.forEach { name ->
            scrollTo(name)
            // The last rows can sit under the bottom "Clean" bar, where a synthetic tap lands on
            // the bar instead; trigger the row's click action directly. Real users just scroll.
            compose.onNodeWithText(name).performSemanticsAction(SemanticsActions.OnClick)
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text(CleanerR.string.cleaner_selected, names.size, ""), substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun cleanSelected() {
        compose.onNodeWithTag(CLEAN_BUTTON_TAG).assertIsEnabled().performClick()
        val confirm = text(CleanerR.string.cleaner_confirm_action)
        runCatching { compose.waitUntilAtLeastOneExists(hasText(confirm), 5_000) }
            .onFailure { screenshot("99_no_confirm_dialog") }
            .getOrThrow()
        compose.onNodeWithText(confirm).performClick()
    }

    private fun scrollTo(label: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
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
