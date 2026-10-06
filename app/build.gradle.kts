plugins {
    alias(libs.plugins.sysclean.android.application)
    alias(libs.plugins.sysclean.android.compose)
    alias(libs.plugins.sysclean.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "vn.sysclean"

    defaultConfig {
        applicationId = "vn.sysclean"
        versionCode = 6
        versionName = "0.6.0"
    }

    androidResources {
        // Ship only the languages the app is translated into; drops library translations.
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("en", "vi")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.privilege)
    implementation(projects.core.data)

    implementation(projects.feature.dashboard)
    implementation(projects.feature.deviceinfo)
    implementation(projects.feature.apps)
    implementation(projects.feature.settings)
    implementation(projects.feature.cleaner)
    implementation(projects.feature.trash)
    implementation(projects.feature.widget)
    implementation(projects.feature.performance)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
