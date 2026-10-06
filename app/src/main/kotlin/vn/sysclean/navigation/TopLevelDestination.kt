package vn.sysclean.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import vn.sysclean.R
import vn.sysclean.feature.apps.AppsRoute
import vn.sysclean.feature.dashboard.DashboardRoute
import vn.sysclean.feature.deviceinfo.DeviceInfoRoute
import vn.sysclean.feature.performance.PerformanceRoute
import vn.sysclean.feature.settings.SettingsRoute
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: KClass<*>,
    val startRoute: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val label: Int,
) {
    DASHBOARD(DashboardRoute::class, DashboardRoute, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, R.string.nav_dashboard),
    PERFORMANCE(PerformanceRoute::class, PerformanceRoute, Icons.Filled.Speed, Icons.Outlined.Speed, R.string.nav_performance),
    DEVICE(DeviceInfoRoute::class, DeviceInfoRoute(), Icons.Filled.PhoneAndroid, Icons.Outlined.PhoneAndroid, R.string.nav_device),
    APPS(AppsRoute::class, AppsRoute(), Icons.Filled.Apps, Icons.Outlined.Apps, R.string.nav_apps),
    SETTINGS(SettingsRoute::class, SettingsRoute, Icons.Filled.Settings, Icons.Outlined.Settings, R.string.nav_settings),
}
