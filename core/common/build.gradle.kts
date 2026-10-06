plugins {
    alias(libs.plugins.sysclean.android.library)
    alias(libs.plugins.sysclean.hilt)
}

android {
    namespace = "vn.sysclean.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
