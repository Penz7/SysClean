plugins {
    alias(libs.plugins.sysclean.android.feature)
}

android {
    namespace = "vn.sysclean.feature.dashboard"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.privilege)
    implementation(projects.core.scanner)
    implementation(projects.core.cleaner)
}
