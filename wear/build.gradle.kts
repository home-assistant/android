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

        versionName = project.version.toString()
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
        kotlinCompilerExtensionVersion = "1.4.8"
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

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.material)

    implementation(libs.wear)
    implementation(libs.core.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.wear.input)
    implementation(libs.wear.remote.interactions)
    implementation(libs.wear.phone.interactions)

    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.okhttp)

    implementation(libs.iconics.core)
    implementation(libs.appcompat)
    implementation(libs.community.material.typeface)
    implementation(libs.iconics.compose)

    implementation(libs.activity.ktx)
    implementation(libs.activity.compose)
    implementation(libs.compiler)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.uiTooling)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.navigation)

    implementation(libs.guava)
    implementation(libs.androidx.tiles)
    implementation(libs.androidx.tiles.material)

    implementation(libs.androidx.watchface.complications.data.source.ktx)

    implementation(libs.androidx.health.services.client)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}

// https://github.com/google/guava/releases/tag/v32.1.0: Reporting dependencies that overlap with Guava
configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("com.google.guava:listenablefuture") {
        select("com.google.guava:guava:0")
    }
}
