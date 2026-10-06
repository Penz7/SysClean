plugins {
    alias(libs.plugins.sysclean.android.library)
    alias(libs.plugins.sysclean.hilt)
}

android {
    namespace = "vn.sysclean.core.privilege"
    buildFeatures {
        aidl = true
    }
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
