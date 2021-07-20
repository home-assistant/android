import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-android-extensions")
    id("com.google.firebase.appdistribution")
    id("com.github.triplet.play")
    id("com.google.gms.google-services")
}

android {
    compileSdkVersion(Config.Android.compileSdk)

    ndkVersion = Config.Android.ndk

    defaultConfig {
        applicationId = "io.homeassistant.companion.android"
        minSdkVersion(Config.Android.minSdk)
        targetSdkVersion(Config.Android.targetSdk)

        versionName = System.getenv("VERSION") ?: "LOCAL"
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

        manifestPlaceholders["sentryRelease"] = "$applicationId@$versionName"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments(mapOf("room.incremental" to "true"))
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
            isV1SigningEnabled = true
            isV2SigningEnabled = true
        }
    }

    buildTypes {
        named("debug").configure {
            applicationIdSuffix = ".debug"
        }
        named("release").configure {
            isDebuggable = false
            isJniDebuggable = false
            isZipAlignEnabled = true
            signingConfig = signingConfigs.getByName("release")
        }
    }
    flavorDimensions("version")
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

    lintOptions {
        isAbortOnError = false
        disable("MissingTranslation")
    }
}

play {
    serviceAccountCredentials.set(file("playStorePublishServiceCredentialsFile.json"))
    track.set("beta")
    resolutionStrategy.set(ResolutionStrategy.IGNORE)
}

dependencies {
    implementation(project(":common"))

    implementation(Config.Dependency.Misc.blurView)
    implementation(Config.Dependency.Misc.altBeacon)
    implementation(Config.Dependency.Misc.iconDialog)
    implementation(Config.Dependency.Misc.iconDialogMaterial)
    implementation(Config.Dependency.Misc.emoji) {
        exclude(group = "org.json", module = "json")
    }

    implementation(Config.Dependency.Kotlin.core)
    implementation(Config.Dependency.Kotlin.reflect)
    implementation(Config.Dependency.Kotlin.coroutines)
    implementation(Config.Dependency.Kotlin.coroutinesAndroid)

    implementation(Config.Dependency.Google.dagger)
    kapt(Config.Dependency.Google.daggerCompiler)

    implementation(Config.Dependency.AndroidX.appcompat)
    implementation(Config.Dependency.AndroidX.lifecycle)
    implementation(Config.Dependency.AndroidX.constraintlayout)
    implementation(Config.Dependency.AndroidX.recyclerview)
    implementation(Config.Dependency.AndroidX.preference)
    implementation(Config.Dependency.AndroidX.navigationFragment)
    implementation(Config.Dependency.AndroidX.navigationUi)
    implementation(Config.Dependency.Google.material)

    implementation(Config.Dependency.AndroidX.roomRuntime)
    implementation(Config.Dependency.AndroidX.roomKtx)
    kapt(Config.Dependency.AndroidX.roomCompiler)

    implementation(Config.Dependency.Misc.jackson)
    implementation(Config.Dependency.Square.okhttp)
    implementation(Config.Dependency.Square.picasso)

    "fullImplementation"(Config.Dependency.Play.location)
    "fullImplementation"(Config.Dependency.Firebase.core)
    "fullImplementation"(Config.Dependency.Firebase.iid)
    "fullImplementation"(Config.Dependency.Firebase.messaging)
    "fullImplementation"(Config.Dependency.Misc.sentry)
    "fullImplementation"(Config.Dependency.Kotlin.coroutinesPlayServices)

    implementation(Config.Dependency.AndroidX.workManager)
    implementation(Config.Dependency.AndroidX.biometric)
    implementation(Config.Dependency.AndroidX.webKit)

    testImplementation(Config.Dependency.Testing.spek2Jvm)
    testImplementation(Config.Dependency.Testing.spek2JUnit)
    testImplementation(Config.Dependency.Testing.assertJ)
    testImplementation(Config.Dependency.Testing.mockk)
    testImplementation(Config.Dependency.Kotlin.coroutinesTest)
    testImplementation(Config.Dependency.Misc.altBeacon)

    implementation(Config.Dependency.Misc.exoCore)
    implementation(Config.Dependency.Misc.exoHls)
    implementation(Config.Dependency.Misc.exoUi)
    implementation(Config.Dependency.Misc.exoCronet)
}

// Disable to fix memory leak and be compatible with the configuration cache.
configure<GoogleServicesPluginConfig> {
    disableVersionCheck = true
}
