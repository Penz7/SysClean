plugins {
    alias(libs.plugins.sysclean.android.feature)
}

android {
    namespace = "vn.sysclean.feature.deviceinfo"
}

dependencies {
    implementation(projects.core.data)
}
