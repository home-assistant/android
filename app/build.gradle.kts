plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-android-extensions")
    id("com.google.firebase.appdistribution")
    id("com.github.triplet.play") version "2.7.5"
    id("io.fabric")
}

apply(plugin = "com.google.firebase.appdistribution")

buildscript {
    repositories {
        google()
        maven(url = "https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath("com.github.triplet.gradle:play-publisher:2.7.5")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.6.0.0")
    }
}

android {
    compileSdkVersion(Config.Android.compileSdk)

    defaultConfig {
        applicationId = "io.homeassistant.companion.android"
        minSdkVersion(Config.Android.minSdk)
        targetSdkVersion(Config.Android.targetSdk)

        val vCode: Int = "${System.getenv("VERSION_CODE") ?: 1}".toInt()
        versionCode = vCode
        versionName = "${Config.version}-$vCode"
    }

    viewBinding {
        isEnabled = true
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
        }
        named("release").configure {
            isDebuggable = false
            isJniDebuggable = false
            isZipAlignEnabled = true
            signingConfig = signingConfigs.getByName("release")
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

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Config.Dependency.Kotlin.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Config.Dependency.Kotlin.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Config.Dependency.Kotlin.coroutines}")

    implementation("com.google.dagger:dagger:${Config.Dependency.Google.dagger}")
    kapt("com.google.dagger:dagger-compiler:${Config.Dependency.Google.dagger}")

    implementation("androidx.appcompat:appcompat:${Config.Dependency.AndroidX.appcompat}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Config.Dependency.AndroidX.lifecycle}")
    implementation("androidx.constraintlayout:constraintlayout:${Config.Dependency.AndroidX.constraintlayout}")
    implementation("androidx.recyclerview:recyclerview:${Config.Dependency.AndroidX.recyclerview}")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("com.google.android.material:material:1.1.0")

    implementation("com.jakewharton.threetenabp:threetenabp:${Config.Dependency.Misc.threeTenAbp}") {
        exclude(group = "org.threeten")
    }

    implementation("com.crashlytics.sdk.android:crashlytics:2.10.1")

    implementation("com.lokalise.android:sdk:2.0.0-beta-5")

    implementation("com.google.android.gms:play-services-location:17.0.0")
    implementation("com.google.firebase:firebase-core:17.3.0")
    implementation("com.google.firebase:firebase-iid:20.1.5")
    implementation("com.google.firebase:firebase-messaging:20.1.5")

    implementation("androidx.work:work-runtime-ktx:2.3.4")
    implementation("androidx.biometric:biometric:1.0.1")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Config.Dependency.Testing.spek2}")
    testImplementation("org.spekframework.spek2:spek-runner-junit5:${Config.Dependency.Testing.spek2}")
    testImplementation("org.assertj:assertj-core:${Config.Dependency.Testing.assertJ}")
    testImplementation("io.mockk:mockk:${Config.Dependency.Testing.mockk}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Config.Dependency.Kotlin.coroutines}")
}

// This plugin must stay at the bottom
// https://developers.google.com/android/guides/google-services-plugin
apply(plugin = "com.google.gms.google-services")