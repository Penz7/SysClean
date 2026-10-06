package vn.sysclean.feature.dashboard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import vn.sysclean.core.model.JunkCategory

@Serializable
data object DashboardRoute

fun NavGraphBuilder.dashboardScreen(
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenCategory: (JunkCategory) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenUnusedApps: () -> Unit,
    onOpenBloatware: () -> Unit,
    onOpenShizukuSetup: () -> Unit,
) {
    composable<DashboardRoute> {
        DashboardScreen(
            onOpenSettings = onOpenSettings,
            onOpenMemory = onOpenMemory,
            onOpenBattery = onOpenBattery,
            onOpenCategory = onOpenCategory,
            onOpenTrash = onOpenTrash,
            onOpenUnusedApps = onOpenUnusedApps,
            onOpenBloatware = onOpenBloatware,
            onOpenShizukuSetup = onOpenShizukuSetup,
        )
    }
}
