import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing key. Android only installs an update over the existing app when both
// are signed with the same key, so every build must use this one key.
// - On GitHub: read from the repository secrets (see README).
// - On this PC: read from signing/keystore.properties (never uploaded).
val signingProps = Properties().apply {
    val file = rootProject.file("signing/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(prop)

val keystorePath = signingValue("DRIFTWOOD_KEYSTORE_PATH", "storeFile")
val appVersion = "1.0.0"

val hasReleaseKey = keystorePath != null && rootProject.file(keystorePath).exists()

android {
    namespace = "com.driftwood.browser"
    // GeckoView 158 needs Android API 37.2 to compile against.
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.driftwood.browser"
        minSdk = 29
        targetSdk = 36
        // Version: raise it for every release (see README, "What the version numbers mean").
        // The release tag must match it: version 1.0.1 is released with the tag v1.0.1.
        versionName = appVersion
        // Android only installs an update with a higher versionCode: 1.2.3 -> 10203.
        versionCode = appVersion.split(".").map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystorePath!!)
                storePassword = signingValue("DRIFTWOOD_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("DRIFTWOOD_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("DRIFTWOOD_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                // No key available: still produce an installable APK, but it can't
                // update an app signed with the real key.
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Compress the engine and code inside the APK (otherwise it's ~225 MB).
        jniLibs.useLegacyPackaging = true
        dex.useLegacyPackaging = true
    }
}

dependencies {
    // Firefox's engine, 64-bit ARM build (what virtually all current phones use).
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:158.0.20261007201254")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.biometric:biometric:1.1.0")
}
