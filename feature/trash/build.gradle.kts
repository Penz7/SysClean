plugins {
    alias(libs.plugins.sysclean.android.feature)
}

android {
    namespace = "vn.sysclean.feature.trash"
}

dependencies {
    implementation(projects.core.data)
}
