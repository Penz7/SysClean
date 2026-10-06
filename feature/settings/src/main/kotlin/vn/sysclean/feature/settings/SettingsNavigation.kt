package vn.sysclean.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object WhitelistRoute

/**
 * [onAddWidget] asks the launcher to pin the widget and returns false when it cannot; the
 * widget lives in another feature module, so the app wires it in.
 */
fun NavGraphBuilder.settingsScreen(
    onOpenTrash: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onAddWidget: () -> Boolean,
) {
    composable<SettingsRoute> {
        SettingsScreen(onOpenTrash = onOpenTrash, onOpenWhitelist = onOpenWhitelist, onAddWidget = onAddWidget)
    }
}

fun NavGraphBuilder.whitelistScreen(onBack: () -> Unit) {
    composable<WhitelistRoute> { WhitelistScreen(onBack = onBack) }
}
