plugins {
    alias(libs.plugins.sysclean.android.library)
    alias(libs.plugins.sysclean.hilt)
}

android {
    namespace = "vn.sysclean.core.performance"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.privilege)
    implementation(libs.androidx.datastore.preferences)
}
