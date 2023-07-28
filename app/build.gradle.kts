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

    useLibrary("android.car")

    defaultConfig {
        applicationId = "io.homeassistant.companion.android"
        minSdk = 21
        targetSdk = 33

        versionName = project.version.toString()
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

        manifestPlaceholders["sentryRelease"] = "$applicationId@$versionName"
        manifestPlaceholders["sentryDsn"] = System.getenv("SENTRY_DSN") ?: ""

        bundle {
            language {
                enableSplit = false
            }
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    kotlinOptions {
        jvmTarget = "11"
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
    track.set("internal")
    resolutionStrategy.set(ResolutionStrategy.IGNORE)
    // We will depend on the wear commit.
    commit.set(true)
}

dependencies {
    implementation(project(":common"))

    implementation(libs.blurView)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    "fullImplementation"(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    implementation(libs.appcompat)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.preference.ktx)
    implementation(libs.material)
    implementation(libs.fragment.ktx)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.okhttp)
    implementation(libs.picasso)

    "fullImplementation"(libs.play.services.location)
    "fullImplementation"(libs.play.services.home)
    "fullImplementation"(libs.play.services.threadnetwork)
    "fullImplementation"(platform(libs.firebase.bom))
    "fullImplementation"(libs.firebase.messaging)
    "fullImplementation"(libs.sentry.android)
    "fullImplementation"(libs.play.services.wearable)
    "fullImplementation"(libs.wear.remote.interactions)

    implementation(libs.biometric)
    implementation(libs.webkit)

    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.hls)
    implementation(libs.exoplayer.ui)
    "fullImplementation"(libs.extension.cronet)
    "minimalImplementation"(libs.extension.cronet) {
        exclude(group = "com.google.android.gms", module = "play-services-cronet")
    }
    "minimalImplementation"(libs.cronet.embedded)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.animation)
    implementation(libs.compose.compiler)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.uiTooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.themeadapter.material)

    implementation(libs.iconics.core)
    implementation(libs.iconics.compose)
    implementation(libs.community.material.typeface)

    implementation(libs.reorderable)
    implementation(libs.changeLog)

    implementation(libs.car.core)
    "fullImplementation"(libs.car.projected)
}

// Disable to fix memory leak and be compatible with the configuration cache.
configure<GoogleServicesPluginConfig> {
    disableVersionCheck = true
}
