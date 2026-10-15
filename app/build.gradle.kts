import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Personal settings that must not be committed (local.properties is gitignored).
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Release signing key (gitignored). Without it, release builds are unsigned.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "com.deskdeck"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.deskdeck"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.0"

        // Optional Spotify library (R6). Empty = the feature explains how to set it up instead.
        // Morning Brief market data (Twelve Data free key). Empty = market card explains setup.
        buildConfigField("String", "TWELVE_DATA_API_KEY", "\"${localProperties.getProperty("twelvedata.apiKey", "").trim()}\"")
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${localProperties.getProperty("spotify.clientId", "").trim()}\"")
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile").removePrefix("../"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
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
        // Optimised like release (R8, not debuggable) but signed with the debug key, so it installs
        // over a debug build and keeps its data. For daily use on the tablet and for measuring
        // battery/CPU: debug builds run several times slower. No developer tools in it.
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    // The profile build uses release's empty DebugTools.
    sourceSets.getByName("profile").kotlin.srcDir("src/release/java")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    // Exported schemas are committed so future migrations can be written and tested against them.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // Window size classes for split screen and pop-up view (N2).
    implementation(libs.androidx.compose.material3.adaptive)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Only for shuffle/repeat: their state lives in the compat "extra binder", which the
    // framework MediaController cannot read.
    implementation(libs.androidx.media)

    // Album art: Palette for the card colors, Coil for players that send an art URI instead of a bitmap.
    // Coil also loads (and caches) launcher icons for the rail through a small custom fetcher.
    implementation(libs.androidx.palette)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Spotify sign-in in a Custom Tab over DeskDeck, rather than switching to a browser app.
    implementation(libs.androidx.browser)

    // Morning Brief prefetch shortly before wake-up (not exact timing; see README).
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Small user settings (pinned apps now, the settings screen in M5).
    implementation(libs.androidx.datastore.preferences)

    // Lyrics: LRCLIB over Retrofit/Moshi (codegen adapters, no reflection), cached in Room.
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
