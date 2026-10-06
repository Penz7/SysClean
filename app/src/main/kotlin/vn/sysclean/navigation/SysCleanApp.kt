package vn.sysclean.navigation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import vn.sysclean.core.privilege.startActivityWithFallback
import vn.sysclean.feature.apps.AppsRoute
import vn.sysclean.feature.apps.BloatwareRoute
import vn.sysclean.feature.apps.bloatwareScreen
import vn.sysclean.feature.apps.appsScreen
import vn.sysclean.feature.cleaner.CleanCategoryRoute
import vn.sysclean.feature.cleaner.cleanCategoryScreen
import vn.sysclean.feature.dashboard.DashboardRoute
import vn.sysclean.feature.dashboard.dashboardScreen
import vn.sysclean.feature.deviceinfo.DeviceInfoRoute
import vn.sysclean.feature.deviceinfo.DeviceTab
import vn.sysclean.feature.deviceinfo.deviceInfoScreen
import vn.sysclean.feature.performance.BatteryRoute
import vn.sysclean.feature.performance.RamRoute
import vn.sysclean.feature.performance.batteryScreen
import vn.sysclean.feature.performance.performanceScreen
import vn.sysclean.feature.performance.ramScreen
import vn.sysclean.feature.settings.SettingsRoute
import vn.sysclean.feature.settings.ShizukuSetupRoute
import vn.sysclean.feature.settings.WhitelistRoute
import vn.sysclean.feature.settings.shizukuSetupScreen
import vn.sysclean.feature.settings.settingsScreen
import vn.sysclean.feature.settings.whitelistScreen
import vn.sysclean.feature.trash.TrashRoute
import vn.sysclean.feature.trash.trashScreen
import vn.sysclean.feature.widget.WidgetRefresher

@Composable
fun SysCleanApp(
    widgetRefresher: WidgetRefresher,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val openAppDetails: (String) -> Unit = { packageName ->
        context.startActivityWithFallback(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            fallback = null,
        )
    }

    // Detail screens (a junk category, the bin) get the whole screen and a back arrow instead.
    val isTopLevel = currentDestination == null ||
        TopLevelDestination.entries.any { destination -> currentDestination.hierarchy.any { it.hasRoute(destination.route) } }

    Scaffold(
        // Each screen draws its own top bar and handles the status bar inset itself.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (isTopLevel) NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination.startRoute) },
                        icon = {
                            Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, contentDescription = null)
                        },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            dashboardScreen(
                onOpenSettings = { navController.navigateToTopLevel(SettingsRoute) },
                onOpenMemory = { navController.navigateToTopLevel(DeviceInfoRoute(DeviceTab.MEMORY)) },
                onOpenBattery = { navController.navigateToTopLevel(DeviceInfoRoute(DeviceTab.BATTERY)) },
                onOpenCategory = { navController.navigate(CleanCategoryRoute(it)) },
                onOpenTrash = { navController.navigate(TrashRoute) },
                onOpenUnusedApps = { navController.navigateToTopLevel(AppsRoute(filter = "UNUSED")) },
                onOpenBloatware = { navController.navigate(BloatwareRoute) },
                onOpenShizukuSetup = { navController.navigate(ShizukuSetupRoute) },
            )
            performanceScreen(
                onOpenRam = { navController.navigate(RamRoute) },
                onOpenBattery = { navController.navigate(BatteryRoute) },
                onOpenSettings = { navController.navigateToTopLevel(SettingsRoute) },
            )
            ramScreen(onBack = navController::popBackStack)
            batteryScreen(onBack = navController::popBackStack)
            deviceInfoScreen()
            appsScreen(
                onOpenAppDetails = openAppDetails,
                onRequestUsageAccess = { navController.navigateToTopLevel(SettingsRoute) },
                onOpenBloatware = { navController.navigate(BloatwareRoute) },
            )
            bloatwareScreen(onBack = navController::popBackStack)
            settingsScreen(
                onOpenTrash = { navController.navigate(TrashRoute) },
                onOpenWhitelist = { navController.navigate(WhitelistRoute) },
                onAddWidget = widgetRefresher::requestPin,
                onOpenShizukuSetup = { navController.navigate(ShizukuSetupRoute) },
            )
            shizukuSetupScreen(onBack = navController::popBackStack)
            cleanCategoryScreen(onBack = navController::popBackStack, onOpenAppDetails = openAppDetails)
            trashScreen(onBack = navController::popBackStack)
            whitelistScreen(onBack = navController::popBackStack)
        }
    }
}

/** Bottom-bar navigation: one copy of each tab on the back stack, state restored when revisited. */
private fun NavHostController.navigateToTopLevel(route: Any) {
    // A deep link into a specific tab or filter must not restore the previously open state.
    val restore = when (route) {
        is DeviceInfoRoute -> route.tab == DeviceTab.OVERVIEW
        is AppsRoute -> route.filter == null
        else -> true
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = restore
    }
}
