plugins {
    alias(libs.plugins.sysclean.android.feature)
}

android {
    namespace = "vn.sysclean.feature.apps"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.privilege)
    implementation(projects.core.scanner)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
