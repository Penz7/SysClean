plugins {
    alias(libs.plugins.sysclean.android.feature)
}

android {
    namespace = "vn.sysclean.feature.settings"
}

dependencies {
    implementation(projects.core.privilege)
    implementation(projects.core.data)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
}
