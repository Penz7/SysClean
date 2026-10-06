plugins {
    alias(libs.plugins.sysclean.android.library)
    alias(libs.plugins.sysclean.android.room)
    alias(libs.plugins.sysclean.hilt)
}

android {
    namespace = "vn.sysclean.core.database"
}
