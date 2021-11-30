plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.github.triplet.play")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

android {
    compileSdk = 31

    defaultConfig {
        applicationId = "io.homeassistant.companion.android"
        minSdk = 25
        targetSdk = 30

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
        kotlinCompilerExtensionVersion = "1.0.4"
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
    }

    lint {
        disable("MissingTranslation")
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

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.5.2")

    implementation("com.google.android.material:material:1.4.0")

    implementation("androidx.wear:wear:1.2.0")
    implementation("com.google.android.support:wearable:2.8.1")
    implementation("com.google.android.gms:play-services-wearable:17.1.0")
    compileOnly("com.google.android.wearable:wearable:2.8.1")

    implementation("com.google.dagger:hilt-android:2.40.2")
    kapt("com.google.dagger:hilt-android-compiler:2.40.3")

    implementation("com.squareup.okhttp3:okhttp:4.9.3")

    implementation("com.mikepenz:iconics-core:5.3.3")
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("com.mikepenz:community-material-typeface:6.4.95.0-kotlin@aar")
    implementation("com.mikepenz:iconics-compose:5.3.3")

    implementation("androidx.activity:activity:1.4.0")
    implementation("androidx.activity:activity-compose:1.4.0")
    implementation("androidx.compose.compiler:compiler:1.0.5")
    implementation("androidx.compose.foundation:foundation:1.0.5")
    implementation("androidx.compose.ui:ui-tooling:1.0.5")
    implementation("androidx.wear.compose:compose-foundation:1.0.0-alpha11")
    implementation("androidx.wear.compose:compose-material:1.0.0-alpha11")
    implementation("androidx.wear.compose:compose-navigation:1.0.0-alpha11")

    implementation("com.google.guava:guava:31.0.1-android")
    implementation("androidx.wear.tiles:tiles:1.0.0")
}
