// A do-nothing app installed only on test devices, so privileged actions (deep sleep,
// disable, uninstall) are exercised on something that is not the user's own app.
plugins {
    alias(libs.plugins.sysclean.android.application)
}

android {
    namespace = "vn.sysclean.fixture"
    defaultConfig {
        applicationId = "vn.sysclean.fixture"
        versionCode = 1
        versionName = "1.0"
    }
}
