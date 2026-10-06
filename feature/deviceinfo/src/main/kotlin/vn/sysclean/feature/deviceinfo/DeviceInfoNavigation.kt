package vn.sysclean.feature.deviceinfo

import androidx.annotation.Keep
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

// Navigation resolves enum route arguments by class name, so R8 must not rename it.
@Keep
enum class DeviceTab { OVERVIEW, CPU, MEMORY, BATTERY, DISPLAY, STORAGE, SENSORS, CAMERAS, SECURITY }

/** [tab] lets other screens deep-link to a section, e.g. the dashboard's RAM tile. */
@Serializable
data class DeviceInfoRoute(val tab: DeviceTab = DeviceTab.OVERVIEW)

fun NavGraphBuilder.deviceInfoScreen() {
    composable<DeviceInfoRoute> { DeviceInfoScreen() }
}
