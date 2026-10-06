plugins {
    alias(libs.plugins.sysclean.android.library)
    alias(libs.plugins.sysclean.hilt)
}

android {
    namespace = "vn.sysclean.core.cleaner"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.scanner)
    implementation(projects.core.privilege)
}
