plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.github.triplet.play")
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
}

play {
    serviceAccountCredentials.set(file("playStorePublishServiceCredentialsFile.json"))
    track.set("beta")
    resolutionStrategy.set(com.github.triplet.gradle.androidpublisher.ResolutionStrategy.IGNORE)
    commit.set(false)
}

dependencies {
    implementation(project(":common"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")

    implementation("com.google.android.material:material:1.4.0")

    implementation("androidx.wear:wear:1.2.0")
    implementation("com.google.android.support:wearable:2.8.1")
    implementation("com.google.android.gms:play-services-wearable:17.1.0")
    compileOnly("com.google.android.wearable:wearable:2.8.1")

    implementation("com.google.dagger:dagger:2.39.1")
    kapt("com.google.dagger:dagger-compiler:2.39.1")

    implementation("com.mikepenz:iconics-core:5.3.2")
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("com.mikepenz:community-material-typeface:5.8.55.0-kotlin@aar")
    implementation("com.mikepenz:iconics-compose:5.3.2")

    implementation("androidx.activity:activity-compose:1.4.0")
    implementation("androidx.compose.compiler:compiler:1.0.4")
    implementation("androidx.compose.foundation:foundation:1.0.4")
    implementation("androidx.wear.compose:compose-foundation:1.0.0-alpha09")
    implementation("androidx.wear.compose:compose-material:1.0.0-alpha09")
}
