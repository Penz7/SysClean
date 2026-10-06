package vn.sysclean.feature.trash

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object TrashRoute

fun NavGraphBuilder.trashScreen(onBack: () -> Unit) {
    composable<TrashRoute> { TrashScreen(onBack = onBack) }
}
