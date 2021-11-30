import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("com.google.firebase.appdistribution")
    id("com.github.triplet.play")
    id("com.google.gms.google-services")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

android {
    compileSdk = 31

    ndkVersion = "21.3.6528147"

    defaultConfig {
        applicationId = "io.homeassistant.companion.android"
        minSdk = 21
        targetSdk = 31

        versionName = System.getenv("VERSION") ?: "LOCAL"
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

        manifestPlaceholders["sentryRelease"] = "$applicationId@$versionName"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.0.4"
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }

    firebaseAppDistribution {
        serviceCredentialsFile = "firebaseAppDistributionServiceCredentialsFile.json"
        releaseNotesFile = "./app/build/outputs/changelogBeta"
        groups = "continuous-deployment"
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
    flavorDimensions.add("version")
    productFlavors {
        create("minimal") {
            applicationIdSuffix = ".minimal"
            versionNameSuffix = "-minimal"
        }
        create("full") {
            applicationIdSuffix = ""
            versionNameSuffix = "-full"
        }

        // Generate a list of application ids into BuildConfig
        val values = productFlavors.joinToString {
            "\"${it.applicationId ?: defaultConfig.applicationId}${it.applicationIdSuffix}\""
        }

        defaultConfig.buildConfigField("String[]", "APPLICATION_IDS", "{$values}")
    }

    playConfigs {
        register("minimal") {
            enabled.set(false)
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform {
            includeEngines("spek2")
        }
    }

    lint {
        isAbortOnError = false
        disable("MissingTranslation")
    }

    kapt {
        correctErrorTypes = true
    }
}

play {
    serviceAccountCredentials.set(file("playStorePublishServiceCredentialsFile.json"))
    track.set("beta")
    resolutionStrategy.set(ResolutionStrategy.IGNORE)
    // We will depend on the wear commit.
    commit.set(true)
}

dependencies {
    implementation(project(":common"))

    implementation("com.github.Dimezis:BlurView:version-1.6.6")
    implementation("org.altbeacon:android-beacon-library:2.19.3")
    implementation("com.maltaisn:icondialog:3.3.0")
    implementation("com.maltaisn:iconpack-community-material:5.3.45")
    implementation("com.vdurmont:emoji-java:5.1.1") {
        exclude(group = "org.json", module = "json")
    }

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")

    implementation("com.google.dagger:hilt-android:2.40.2")
    kapt("com.google.dagger:hilt-android-compiler:2.40.3")

    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")
    implementation("com.google.android.material:material:1.4.0")

    implementation("androidx.wear:wear-remote-interactions:1.0.0")
    implementation("com.google.android.gms:play-services-wearable:17.1.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.squareup.picasso:picasso:2.8")

    "fullImplementation"("com.google.android.gms:play-services-location:18.0.0")
    "fullImplementation"("com.google.firebase:firebase-core:20.0.0")
    "fullImplementation"("com.google.firebase:firebase-iid:21.1.0")
    "fullImplementation"("com.google.firebase:firebase-messaging:23.0.0")
    "fullImplementation"("io.sentry:sentry-android:5.4.2")
    "fullImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.5.2")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.webkit:webkit:1.4.0")

    implementation("com.google.android.exoplayer:exoplayer-core:2.15.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.15.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.15.1")
    implementation("com.google.android.exoplayer:extension-cronet:2.15.1")

    implementation("androidx.compose.animation:animation:1.0.5")
    implementation("androidx.compose.compiler:compiler:1.0.5")
    implementation("androidx.compose.foundation:foundation:1.0.5")
    implementation("androidx.compose.material:material:1.0.5")
    implementation("androidx.compose.material:material-icons-core:1.0.5")
    implementation("androidx.compose.material:material-icons-extended:1.0.5")
    implementation("androidx.compose.runtime:runtime:1.0.5")
    implementation("androidx.compose.ui:ui:1.0.5")
    implementation("androidx.compose.ui:ui-tooling:1.0.5")
    implementation("androidx.activity:activity-compose:1.4.0")
    implementation("androidx.navigation:navigation-compose:2.4.0-beta02")
    implementation("com.google.android.material:compose-theme-adapter:1.1.0")
    implementation("com.google.accompanist:accompanist-appcompat-theme:0.20.2")

    implementation("com.mikepenz:iconics-core:5.3.3")
    implementation("com.mikepenz:community-material-typeface:6.4.95.0-kotlin@aar")
}

// Disable to fix memory leak and be compatible with the configuration cache.
configure<GoogleServicesPluginConfig> {
    disableVersionCheck = true
}
