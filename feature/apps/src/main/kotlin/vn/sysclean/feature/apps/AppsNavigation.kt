package vn.sysclean.feature.apps

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** [filter] is an [AppFilter] name, e.g. to open straight on unused apps from the dashboard. */
@Serializable
data class AppsRoute(val filter: String? = null)

@Serializable
data object BloatwareRoute

fun NavGraphBuilder.appsScreen(
    onOpenAppDetails: (packageName: String) -> Unit,
    onRequestUsageAccess: () -> Unit,
    onOpenBloatware: () -> Unit,
) {
    composable<AppsRoute> {
        AppsScreen(
            onOpenAppDetails = onOpenAppDetails,
            onRequestUsageAccess = onRequestUsageAccess,
            onOpenBloatware = onOpenBloatware,
        )
    }
}

fun NavGraphBuilder.bloatwareScreen(onBack: () -> Unit) {
    composable<BloatwareRoute> { BloatwareScreen(onBack = onBack) }
}
