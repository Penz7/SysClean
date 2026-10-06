package vn.sysclean.feature.performance

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PerformanceRoute

@Serializable
data object RamRoute

@Serializable
data object BatteryRoute

fun NavGraphBuilder.performanceScreen(onOpenRam: () -> Unit, onOpenBattery: () -> Unit, onOpenSettings: () -> Unit) {
    composable<PerformanceRoute> {
        PerformanceScreen(onOpenRam = onOpenRam, onOpenBattery = onOpenBattery, onOpenSettings = onOpenSettings)
    }
}

fun NavGraphBuilder.batteryScreen(onBack: () -> Unit) {
    composable<BatteryRoute> { BatteryScreen(onBack = onBack) }
}

fun NavGraphBuilder.ramScreen(onBack: () -> Unit) {
    composable<RamRoute> { RamScreen(onBack = onBack) }
}
