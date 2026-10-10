import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.substichoose"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.substichoose"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3-hackathon"

        // USDA FoodData Central key for nutrition lookups. Put USDA_API_KEY=... in local.properties
        // (not committed). DEMO_KEY works but allows only a few lookups per hour.
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "USDA_API_KEY", "\"${localProps.getProperty("USDA_API_KEY") ?: "DEMO_KEY"}\"")
    }

    // Release signing: keystore.properties + release.jks (both git-ignored). Keep them: updates to an
    // installed app must be signed with the same key. Without them, release falls back to debug signing.
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Real phones only (64-bit ARM): Gemma needs a modern 64-bit phone anyway, and dropping
            // emulator/32-bit native libraries roughly halves the APK.
            ndk { abiFilters += "arm64-v8a" }
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // litertlm uses latest.release; newer releases (and the kotlin-stdlib/coroutines they pull in)
        // may be built with a newer Kotlin than 2.1.21, whose metadata this compiler would otherwise reject.
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // The Gemma model is NOT bundled (about 2.6 GB). It is copied to the phone with adb; see README.
    androidResources {
        noCompress += "litertlm"
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")

    // Camera + on-device text recognition
    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // On-device Gemma (LiteRT-LM). Pinned to the version that first built cleanly.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

    // Storage
    val room = "2.7.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // JSON + coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
