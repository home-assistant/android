plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.github.triplet.play")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
    id("com.google.gms.google-services")
}

android {
    namespace = "io.homeassistant.companion.android"

    compileSdk = 33

    defaultConfig {
        applicationId = "io.homeassistant.companion.android"
        minSdk = 26
        targetSdk = 32

        versionName = System.getenv("VERSION") ?: "LOCAL"
        // We add 1 because the app and wear versions need to have different version codes.
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1) + 1

        javaCompileOptions {
            annotationProcessorOptions {
                arguments(mapOf("room.incremental" to "true"))
            }
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.7"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release_keystore.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEYSTORE_ALIAS") ?: ""
            keyPassword = System.getenv("KEYSTORE_ALIAS_PASSWORD") ?: ""
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        named("debug").configure {
            applicationIdSuffix = ".debug"
        }
        named("release").configure {
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }

    lint {
        disable += "MissingTranslation"
    }

    kapt {
        correctErrorTypes = true
    }
}

play {
    serviceAccountCredentials.set(file("playStorePublishServiceCredentialsFile.json"))
    track.set("internal")
    resolutionStrategy.set(com.github.triplet.gradle.androidpublisher.ResolutionStrategy.IGNORE)
    commit.set(false)
}

dependencies {
    implementation(project(":common"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.1")

    implementation("com.google.android.material:material:1.9.0")

    implementation("androidx.wear:wear:1.2.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("com.google.android.gms:play-services-wearable:18.0.0")
    implementation("androidx.wear:wear-input:1.2.0-alpha02")
    implementation("androidx.wear:wear-remote-interactions:1.0.0")
    implementation("androidx.wear:wear-phone-interactions:1.0.1")

    implementation("com.google.dagger:hilt-android:2.46.1")
    kapt("com.google.dagger:hilt-android-compiler:2.46.1")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.5")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    implementation("com.mikepenz:iconics-core:5.4.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.mikepenz:community-material-typeface:7.0.96.0-kotlin@aar")
    implementation("com.mikepenz:iconics-compose:5.4.0")

    implementation("androidx.activity:activity-ktx:1.7.1")
    implementation("androidx.activity:activity-compose:1.7.1")
    implementation("androidx.compose.compiler:compiler:1.4.7")
    implementation(platform("androidx.compose:compose-bom:2023.05.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.wear.compose:compose-foundation:1.1.2")
    implementation("androidx.wear.compose:compose-material:1.1.2")
    implementation("androidx.wear.compose:compose-navigation:1.1.2")

    implementation("com.google.guava:guava:32.0.0-android")
    implementation("androidx.wear.tiles:tiles:1.1.0")
    implementation("androidx.wear.tiles:tiles-material:1.1.0")

    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.1.1")

    implementation("androidx.health:health-services-client:1.0.0-beta03")

    implementation(platform("com.google.firebase:firebase-bom:32.1.0"))
    implementation("com.google.firebase:firebase-messaging")
}
