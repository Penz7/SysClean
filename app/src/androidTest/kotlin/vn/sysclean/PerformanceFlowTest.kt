package vn.sysclean

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import vn.sysclean.feature.performance.R as PerfR

/**
 * Performance tab on a real device with Shizuku. The host opens vn.sysclean.fixture first
 * (adb shell am start -n vn.sysclean.fixture/.FixtureActivity) so it is a background app.
 * Only the fixture is put to sleep and only TRIM runs; nothing of the user's is changed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PerformanceFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    /** The phone's own animation settings, put back even if the test fails half-way. */
    private val animationKeys = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    private lateinit var animationsBefore: Map<String, String>

    private fun shell(command: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText().trim()
        }

    @Before
    fun rememberAnimations() {
        animationsBefore = animationKeys.associateWith { shell("settings get global $it") }
    }

    @After
    fun restoreAnimations() {
        animationsBefore.forEach { (key, value) ->
            if (value == "null") shell("settings delete global $key") else shell("settings put global $key $value")
        }
    }

    @Test
    fun diagnoseAnimationsTrimAndDeepSleepFixture() {
        navigateTo(R.string.nav_performance)
        compose.waitUntilAtLeastOneExists(hasText(text(PerfR.string.perf_diagnosis)), 60_000)
        assumeTrue(
            "Shizuku or root required",
            compose.onAllNodes(hasText(text(PerfR.string.perf_optimize))).fetchSemanticsNodes().isNotEmpty() ||
                runCatching { scrollTo(text(PerfR.string.perf_optimize)) }.isSuccess,
        )
        scrollTo(text(PerfR.string.perf_diagnosis))
        screenshot("60_performance")

        // Animations: switch to 0.5x and back, checking the real system setting.
        val resolver = compose.activity.contentResolver
        val before = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val animationSwitch = hasText(text(PerfR.string.perf_animations_fast)) and isToggleable()
        scrollTo(text(PerfR.string.perf_animations_fast))
        compose.onNode(animationSwitch).performClick()
        compose.waitUntil(15_000) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != before }
        // Wait for the re-measured report showing the new value (whatever the phone started at),
        // or the second tap would act on the stale switch state.
        val toggled = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        compose.waitUntilAtLeastOneExists(hasText(": ${toggled}x", substring = true), 90_000)
        scrollTo(text(PerfR.string.perf_animations_fast))
        compose.onNode(animationSwitch).performClick()
        compose.waitUntil(15_000) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == before }

        // One-tap with only TRIM selected.
        scrollTo(text(PerfR.string.perf_optimize_button))
        uncheckEverythingBut(text(PerfR.string.perf_step_trim))
        compose.onNodeWithText(text(PerfR.string.perf_optimize_button)).performClick()
        compose.waitUntil(300_000) {
            compose.onAllNodes(hasText(text(PerfR.string.perf_result_trimmed), substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("61_optimized")
        // Free RAM was not chosen, and the result says so instead of showing a "change".
        compose.onNodeWithText(text(PerfR.string.perf_result_ram_not_chosen)).assertExists()

        // RAM manager: the fixture sits in background RAM; deep sleep it, then wake it.
        scrollTo(text(PerfR.string.perf_ram_manager_desc))
        compose.onNodeWithText(text(PerfR.string.perf_ram_manager_desc)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(PerfR.string.ram_reducible)), 90_000)
        screenshot("62_ram")
        val fixture = "SysClean Test Fixture"
        scrollTo(fixture)
        clickButtonInRowOf(fixture, text(PerfR.string.ram_deep_sleep))
        compose.waitUntilAtLeastOneExists(hasText(text(PerfR.string.ram_slept, fixture)), 30_000)
        // After the reload the fixture is listed under "Asleep or disabled" (it left RAM when stopped).
        compose.waitUntil(90_000) { runCatching { scrollTo(text(PerfR.string.ram_reduced)) }.isSuccess }
        scrollTo(fixture)
        screenshot("63_ram_slept")
        clickButtonInRowOf(fixture, text(PerfR.string.ram_wake))
        compose.waitUntilAtLeastOneExists(hasText(text(PerfR.string.ram_woken, fixture)), 30_000)
        assertEquals(before, Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }

    /**
     * Leaves only [keep] ticked among the one-tap steps. A row is clicked only if it is ticked:
     * clicking an unticked "deep sleep" row would put the user's own apps to sleep.
     */
    private fun uncheckEverythingBut(keep: String) {
        listOf(
            PerfR.string.perf_step_compile,
            PerfR.string.perf_step_sleep,
            PerfR.string.perf_step_free_ram,
            PerfR.string.perf_step_animations,
        ).forEach { id ->
            // The part after the number is unique; "Optimize …" would also match "Optimize now".
            val suffix = text(id, 0).substringAfter("0").trim()
            val ticked = compose.onAllNodes(hasText(suffix, substring = true) and isOn())
            if (ticked.fetchSemanticsNodes().isNotEmpty()) ticked[0].performSemanticsAction(SemanticsActions.OnClick)
        }
        compose.onNode(hasText(keep) and isOn()).fetchSemanticsNode()
    }

    private fun clickButtonInRowOf(label: String, action: String) {
        val row = compose.onNodeWithText(label).fetchSemanticsNode()
        val buttons = compose.onAllNodes(hasText(action) and hasClickAction()).fetchSemanticsNodes()
        val nearest = buttons.minBy { kotlin.math.abs(it.boundsInRoot.center.y - row.boundsInRoot.center.y) }
        compose.onAllNodes(hasText(action) and hasClickAction())[buttons.indexOf(nearest)]
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun scrollTo(label: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label, substring = true))
    }

    private fun navigateTo(label: Int) {
        compose.onNode(hasText(text(label)) and hasClickAction() and !hasAnyAncestor(hasScrollAction())).performClick()
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(compose.activity.getExternalFilesDir(null), "smoke").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
