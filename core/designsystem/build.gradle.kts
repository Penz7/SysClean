plugins {
    alias(libs.plugins.sysclean.android.library)
    alias(libs.plugins.sysclean.android.compose)
}

android {
    namespace = "vn.sysclean.core.designsystem"
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
}
