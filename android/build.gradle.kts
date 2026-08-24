plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.environmentVariable("NSPANEL_RELEASE_KEYSTORE").orNull
val releaseKeyAlias = providers.environmentVariable("NSPANEL_RELEASE_KEY_ALIAS").orNull
val releaseStorePassword = providers.environmentVariable("NSPANEL_RELEASE_STORE_PASSWORD").orNull
val releaseKeyPassword = providers.environmentVariable("NSPANEL_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseKeyAlias,
    releaseStorePassword,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.hacompanion.panel"
    compileSdk = 34

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.0"
    }

    defaultConfig {
        applicationId = "dev.hacompanion.panel"
        minSdk = 26
        targetSdk = 28
        versionCode = providers.environmentVariable("NSPANEL_VERSION_CODE").orNull?.toInt() ?: 7
        versionName = providers.environmentVariable("NSPANEL_VERSION_NAME").orNull ?: "0.3.3-alpha"
        ndk {
            // The target NSPanel Pro and development emulator are both ARM64.
            // Avoid packaging three unused libwebrtc binaries.
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails release builds unless the permanent external signing credentials are configured."
    doLast {
        require(releaseSigningConfigured) {
            "Release signing is not configured. Set NSPANEL_RELEASE_KEYSTORE, " +
                "NSPANEL_RELEASE_KEY_ALIAS, NSPANEL_RELEASE_STORE_PASSWORD, and NSPANEL_RELEASE_KEY_PASSWORD."
        }
        require(file(releaseKeystorePath!!).isFile) { "Release keystore does not exist: $releaseKeystorePath" }
    }
}

tasks.matching { it.name in setOf("assembleRelease", "bundleRelease") }.configureEach {
    dependsOn(verifyReleaseSigning)
}

dependencies {
    // SPIKE ONLY - measuring what Compose costs on this hardware.
    implementation(platform("androidx.compose:compose-bom:2023.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.6.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.webrtc-sdk:android:144.7559.08")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
