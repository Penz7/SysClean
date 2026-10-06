plugins {
    alias(libs.plugins.sysclean.android.feature)
}

android {
    namespace = "vn.sysclean.feature.cleaner"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.privilege)
    implementation(projects.core.scanner)
    implementation(projects.core.cleaner)
    implementation(libs.androidx.activity.compose)
}
