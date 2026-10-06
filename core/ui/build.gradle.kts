plugins {
    alias(libs.plugins.sysclean.android.library)
    alias(libs.plugins.sysclean.android.compose)
}

android {
    namespace = "vn.sysclean.core.ui"
}

dependencies {
    api(projects.core.model)
    api(projects.core.designsystem)
    implementation(projects.core.common)
}
