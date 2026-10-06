plugins {
    alias(libs.plugins.sysclean.android.library)
    alias(libs.plugins.sysclean.hilt)
}

android {
    namespace = "vn.sysclean.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.privilege)
    implementation(projects.core.database)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
}
