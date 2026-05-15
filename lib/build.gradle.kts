plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.airouter.lib"
    compileSdk = 35
    
    defaultConfig {
        minSdk = 26
        
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }
    
    externalNativeBuild {
        cmake {
            cppFlags += "-std=c++17"
            arguments += "-DLLAMA_BUILD_COMMON=OFF"
        }
    }
    
    buildFeatures {
        compose = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(libs.okhttp)
}
