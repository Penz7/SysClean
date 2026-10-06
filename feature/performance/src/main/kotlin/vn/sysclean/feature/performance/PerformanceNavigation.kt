package vn.sysclean.feature.performance

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PerformanceRoute

@Serializable
data object RamRoute

fun NavGraphBuilder.performanceScreen(onOpenRam: () -> Unit, onOpenSettings: () -> Unit) {
    composable<PerformanceRoute> { PerformanceScreen(onOpenRam = onOpenRam, onOpenSettings = onOpenSettings) }
}

fun NavGraphBuilder.ramScreen(onBack: () -> Unit) {
    composable<RamRoute> { RamScreen(onBack = onBack) }
}
