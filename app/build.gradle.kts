plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-android-extensions")
    id("com.google.firebase.appdistribution")
    id("com.github.triplet.play") version "2.7.5"
}

buildscript {
    repositories {
        google()
        maven(url = Config.Repository.gradle)
    }
    dependencies {
        classpath(Config.Plugin.playPublisher)
        classpath(Config.Plugin.androidJunit5)
    }
}

android {
    compileSdkVersion(Config.Android.compileSdk)

    ndkVersion = Config.Android.ndk

    defaultConfig {
        applicationId = "io.homeassistant.companion.android"
        minSdkVersion(Config.Android.minSdk)
        targetSdkVersion(Config.Android.targetSdk)

        val ver = System.getenv("VERSION") ?: "LOCAL"
        val vCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionCode = vCode
        versionName = "$ver-$vCode"
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
        releaseNotesFile = "app/src/main/play/release-notes/en-US/default.txt"
        groups = "continuous-deployment"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release_keystore.keystore")
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
            manifestPlaceholders["sentryEnabled"] = "false"
        }
        named("release").configure {
            isDebuggable = false
            isJniDebuggable = false
            isZipAlignEnabled = true
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["sentryEnabled"] = "true"
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
    }

    playConfigs {
        register("minimal") {
            isEnabled = false
        }
    }

    testOptions {
        unitTests.apply { isReturnDefaultValues = true }
    }

    tasks.withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
    }

    lintOptions {
        disable("MissingTranslation")
    }
}

play {
    serviceAccountCredentials = file("playStorePublishServiceCredentialsFile.json")
    track = "beta"
    resolutionStrategy = "ignore"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))

    implementation(Config.Dependency.Misc.blurView)
    implementation(Config.Dependency.Misc.iconDialog)
    implementation(Config.Dependency.Misc.iconDialogMaterial)
    implementation(Config.Dependency.Misc.emoji) {
        exclude(group = "org.json", module = "json")
    }

    implementation(Config.Dependency.Kotlin.core)
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
}

// This plugin must stay at the bottom
// https://developers.google.com/android/guides/google-services-plugin
apply(plugin = "com.google.gms.google-services")
