plugins {
    id("com.android.application")
    // AGP 9's built-in Kotlin support replaces the standalone kotlin("android") plugin.
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.reader.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.reader.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }
}

kotlin {
    // Matches the JDK actually available on the build machine; bytecode target stays
    // Java 11 via compileOptions above.
    jvmToolchain(21)
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Readium toolkit - EPUB parsing, rendering and text-to-speech.
    implementation("org.readium.kotlin-toolkit:readium-shared:3.3.0")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.3.0")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.3.0")
    implementation("org.readium.kotlin-toolkit:readium-navigator-media-common:3.3.0")
    implementation("org.readium.kotlin-toolkit:readium-navigator-media-tts:3.3.0")

    // Media3 - background playback session for the TTS navigator.
    implementation("androidx.media3:media3-session:1.10.0")
    implementation("androidx.media3:media3-common-ktx:1.10.0")

    // AndroidX core / lifecycle / fragment-in-compose.
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-compose:1.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Compose UI.
    implementation("androidx.compose.ui:ui:1.10.5")
    implementation("androidx.compose.ui:ui-graphics:1.10.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.5")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.5")
    implementation("androidx.compose.foundation:foundation:1.10.5")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.runtime:runtime:1.10.5")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    // Room database (library + reading progress).
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore (user settings, e.g. TTS speed).
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Hilt dependency injection.
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
