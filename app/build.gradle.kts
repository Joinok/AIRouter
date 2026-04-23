plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.airouter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.airouter"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.2.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "airouter_debug"
            keyAlias = "airouter_debug"
            keyPassword = "airouter_debug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Koin (DI)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.navigation)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // MMKV
    implementation(libs.mmkv)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Image
    implementation(libs.coil.compose)

    // Markdown
    implementation(libs.compose.markdown)
    implementation(libs.compose.markdown.m3)
}
