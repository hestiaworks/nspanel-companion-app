plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.hacompanion.panel"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "dev.hacompanion.panel"
        minSdk = 26
        targetSdk = 28
        versionCode = 7
        versionName = "0.3.3-alpha"
        ndk {
            // The target NSPanel Pro and development emulator are both ARM64.
            // Avoid packaging three unused libwebrtc binaries.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        // This APK is sideloaded onto Android 8-era dedicated panels. Target 28 keeps
        // the legacy boot-launch behavior until it is validated on real hardware.
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.webrtc-sdk:android:144.7559.08")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
