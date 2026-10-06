plugins {
    alias(libs.plugins.sysclean.android.library)
    alias(libs.plugins.sysclean.android.compose)
    alias(libs.plugins.sysclean.hilt)
}

android {
    namespace = "vn.sysclean.feature.widget"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
