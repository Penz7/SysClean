package vn.sysclean.feature.cleaner

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import vn.sysclean.core.model.JunkCategory

/** The category travels as its name so R8 renaming the enum cannot break navigation. */
@Serializable
data class CleanCategoryRoute(val category: String) {
    constructor(category: JunkCategory) : this(category.name)
}

fun NavGraphBuilder.cleanCategoryScreen(
    onBack: () -> Unit,
    onOpenAppDetails: (packageName: String) -> Unit,
) {
    composable<CleanCategoryRoute> {
        CleanCategoryScreen(onBack = onBack, onOpenAppDetails = onOpenAppDetails)
    }
}
