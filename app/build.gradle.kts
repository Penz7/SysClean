import java.util.Properties

plugins {
    alias(libs.plugins.sysclean.android.application)
    alias(libs.plugins.sysclean.android.compose)
    alias(libs.plugins.sysclean.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from keystore.properties (git-ignored, never committed). Without it the
// release build is produced unsigned, so anyone can still build the project.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "vn.sysclean"

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "vn.sysclean"
        versionCode = 9
        versionName = "0.7.1"
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
            signingConfig = signingConfigs.findByName("release")
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
