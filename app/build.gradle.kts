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
    namespace = "io.homeassistant.companion.android"

    compileSdk = 33

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
        kotlinCompilerExtensionVersion = "1.1.1"
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
        abortOnError = false
        disable += "MissingTranslation"
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
    implementation("org.altbeacon:android-beacon-library:2.19.4")
    implementation("com.maltaisn:icondialog:3.3.0")
    implementation("com.maltaisn:iconpack-community-material:5.3.45")
    implementation("com.vdurmont:emoji-java:5.1.1") {
        exclude(group = "org.json", module = "json")
    }

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    implementation("com.google.dagger:hilt-android:2.44")
    kapt("com.google.dagger:hilt-android-compiler:2.44")

    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.5.3")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.picasso:picasso:2.8")

    "fullImplementation"("com.google.android.gms:play-services-location:20.0.0")
    "fullImplementation"(platform("com.google.firebase:firebase-bom:30.4.1"))
    "fullImplementation"("com.google.firebase:firebase-analytics")
    "fullImplementation"("com.google.firebase:firebase-messaging")
    "fullImplementation"("io.sentry:sentry-android:6.4.2")
    "fullImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")
    "fullImplementation"("com.google.android.gms:play-services-wearable:18.0.0")
    "fullImplementation"("androidx.wear:wear-remote-interactions:1.0.0")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.webkit:webkit:1.5.0")

    implementation("com.google.android.exoplayer:exoplayer-core:2.18.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.18.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.18.1")
    "fullImplementation"("com.google.android.exoplayer:extension-cronet:2.18.1")
    "minimalImplementation"("com.google.android.exoplayer:extension-cronet:2.18.1") {
        exclude(group = "com.google.android.gms", module = "play-services-cronet")
    }
    "minimalImplementation"("org.chromium.net:cronet-embedded:105.5195.68")

    implementation("androidx.compose.animation:animation:1.2.1")
    implementation("androidx.compose.compiler:compiler:1.3.1")
    implementation("androidx.compose.foundation:foundation:1.2.1")
    implementation("androidx.compose.material:material:1.2.1")
    implementation("androidx.compose.material:material-icons-core:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.2.1")
    implementation("androidx.compose.runtime:runtime:1.2.1")
    implementation("androidx.compose.ui:ui:1.2.1")
    implementation("androidx.compose.ui:ui-tooling:1.2.1")
    implementation("androidx.activity:activity-compose:1.6.0")
    implementation("androidx.navigation:navigation-compose:2.5.2")
    implementation("com.google.android.material:compose-theme-adapter:1.1.19")
    implementation("com.google.accompanist:accompanist-appcompat-theme:0.25.1")

    implementation("com.mikepenz:iconics-core:5.3.4")
    implementation("com.mikepenz:iconics-compose:5.4.0")
    implementation("com.mikepenz:community-material-typeface:6.4.95.0-kotlin@aar")
    "fullImplementation"("org.burnoutcrew.composereorderable:reorderable:0.9.2")
    implementation("com.github.AppDevNext:ChangeLog:3.4")
}

// Disable to fix memory leak and be compatible with the configuration cache.
configure<GoogleServicesPluginConfig> {
    disableVersionCheck = true
}
