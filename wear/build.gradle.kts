plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.github.triplet.play")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

android {
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
        kotlinCompilerExtensionVersion = "1.1.1"
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
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
        // Temporarily required to implement an interface from Compose because they use @JvmDefault
        // Remove when kotlin-gradle-plugin is >=1.6.20 (https://issuetracker.google.com/issues/217593040)
        freeCompilerArgs = freeCompilerArgs + "-Xjvm-default=all"
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
    track.set("beta")
    resolutionStrategy.set(com.github.triplet.gradle.androidpublisher.ResolutionStrategy.IGNORE)
    commit.set(false)
}

dependencies {
    implementation(project(":common"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.6.4")

    implementation("com.google.android.material:material:1.6.1")

    implementation("androidx.wear:wear:1.2.0")
    implementation("com.google.android.gms:play-services-wearable:18.0.0")
    implementation("androidx.wear:wear-input:1.2.0-alpha02")
    implementation("androidx.wear:wear-remote-interactions:1.0.0")
    implementation("androidx.wear:wear-phone-interactions:1.0.1")

    implementation("com.google.dagger:hilt-android:2.43.2")
    kapt("com.google.dagger:hilt-android-compiler:2.44")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    implementation("com.mikepenz:iconics-core:5.3.4")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.mikepenz:community-material-typeface:6.4.95.0-kotlin@aar")
    implementation("com.mikepenz:iconics-compose:5.3.4")

    implementation("androidx.activity:activity-ktx:1.6.0")
    implementation("androidx.activity:activity-compose:1.6.0")
    implementation("androidx.compose.compiler:compiler:1.3.1")
    implementation("androidx.compose.foundation:foundation:1.2.1")
    implementation("androidx.compose.ui:ui-tooling:1.2.1")
    implementation("androidx.wear.compose:compose-foundation:1.0.2")
    implementation("androidx.wear.compose:compose-material:1.0.2")
    implementation("androidx.wear.compose:compose-navigation:1.0.2")

    implementation("com.google.guava:guava:31.1-android")
    implementation("androidx.wear.tiles:tiles:1.1.0")

    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.1.1")
}
