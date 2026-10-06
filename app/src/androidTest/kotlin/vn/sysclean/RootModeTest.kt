package vn.sysclean

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import vn.sysclean.feature.settings.R as SettingsR

/**
 * Root mode on a device without su: the opt-in must fail gracefully and stay off.
 * Skipped on rooted devices, where it would trigger a real superuser prompt.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RootModeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun enablingRootWithoutSuIsRefusedCleanly() {
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su")
        assumeTrue("device must not be rooted", suPaths.none { File(it).exists() })

        compose.onNode(hasText(text(R.string.nav_settings)) and hasClickAction() and !hasAnyAncestor(hasScrollAction()))
            .performClick()
        val tryRoot = text(SettingsR.string.settings_root_try)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(tryRoot))
        compose.onNodeWithText(tryRoot).performClick()

        compose.waitUntilAtLeastOneExists(hasText(text(SettingsR.string.settings_root_denied)), 15_000)
        // Still offered again, i.e. root mode did not get switched on.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(tryRoot))
    }
}
