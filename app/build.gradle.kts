plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vknxworks.frigatectrl"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vknxworks.frigatectrl"
        minSdk = 35
        targetSdk = 36
        // CI passes the run number so every build installs over the previous one.
        // Bump both to publish a new release (see CHANGELOG.md).
        versionCode = 64
        versionName = "3.9.0"
        // 64-bit ARM phones only (arm64-v8a), like every Ctrl app.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Release signing. On GitHub Actions the build job has no signing key at all: it sets
    // UNSIGNED_RELEASE=true and gets app-release-unsigned.apk, which a separate job signs with apksigner
    // (see .github/workflows/build.yml). An unsigned APK cannot be installed, so nothing unsigned can ship.
    // A keystore from environment variables still works (SIGNING_KEYSTORE_PATH, SIGNING_STORE_PASSWORD,
    // SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD). Local builds without one fall back to the debug key so the
    // APK is always installable; on CI a release build that has neither a key nor UNSIGNED_RELEASE=true
    // fails, so a debug-signed APK is never published as a release.
    val ksPath = System.getenv("SIGNING_KEYSTORE_PATH")
    val hasReleaseKey = !ksPath.isNullOrBlank() && file(ksPath).exists() &&
        listOf("SIGNING_STORE_PASSWORD", "SIGNING_KEY_ALIAS", "SIGNING_KEY_PASSWORD").all { !System.getenv(it).isNullOrBlank() }
    val onCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
    val buildingRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
    val unsignedRelease = System.getenv("UNSIGNED_RELEASE") == "true"
    if (onCi && buildingRelease && !hasReleaseKey && !unsignedRelease) {
        throw GradleException(
            "Release signing key or passwords are missing on CI and UNSIGNED_RELEASE=true is not set - " +
                "refusing to sign the release with the debug key",
        )
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
            // UNSIGNED_RELEASE=true: no signing here at all, the output is app-release-unsigned.apk.
            signingConfig = if (unsignedRelease) null else (signingConfigs.findByName("release") ?: signingConfigs.getByName("debug"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    // No dependency-metadata block in the APK signature: it's encrypted for Google Play only, and F-Droid flags it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.fragment:fragment-ktx:1.8.6")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Background check for new alerts (the fallback when no push from ntfy arrives).
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Unit tests only (not in the app).
    testImplementation("junit:junit:4.13.2")
}
