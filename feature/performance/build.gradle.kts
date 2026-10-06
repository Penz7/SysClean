plugins {
    alias(libs.plugins.sysclean.android.feature)
}

android {
    namespace = "vn.sysclean.feature.performance"
}

dependencies {
    implementation(projects.core.performance)
    implementation(projects.core.privilege)
    implementation(projects.core.data)
    implementation(libs.androidx.activity.compose)
}
