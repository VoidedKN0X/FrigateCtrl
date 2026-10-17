plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vknxworks.frigatectrl"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vknxworks.frigatectrl"
        minSdk = 33
        targetSdk = 35
        // CI passes the run number so every build installs over the previous one.
        // Bump both to publish a new release (see CHANGELOG.md).
        versionCode = 24
        versionName = "3.2.2"
    }

    // Release signing from env (GitHub secrets). Local builds fall back to the debug key so the APK
    // is always installable; on CI a missing key fails the build instead of publishing a debug-signed APK.
    val ksPath = System.getenv("SIGNING_KEYSTORE_PATH")
    val hasReleaseKey = !ksPath.isNullOrBlank() && file(ksPath).exists() &&
        listOf("SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD").all { !System.getenv(it).isNullOrBlank() }
    val onCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
    val buildingRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
    if (onCi && buildingRelease && !hasReleaseKey) {
        throw GradleException("Release signing key or passwords are missing on CI - refusing to sign the release with the debug key")
    }
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    // Backdrop blur for the floating glass tab bar.
    implementation("dev.chrisbanes.haze:haze:1.5.4")
    implementation("dev.chrisbanes.haze:haze-materials:1.5.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")
}
