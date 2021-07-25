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
    compileSdkVersion(30)

    ndkVersion = "21.3.6528147"

    defaultConfig {
        applicationId = "io.homeassistant.companion.android"
        minSdkVersion(21)
        targetSdkVersion(30)

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

    implementation("com.github.Dimezis:BlurView:version-1.6.6")
    implementation("org.altbeacon:android-beacon-library:2+")
    implementation("com.maltaisn:icondialog:3.3.0")
    implementation("com.maltaisn:iconpack-community-material:5.3.45")
    implementation("com.vdurmont:emoji-java:5.1.1") {
        exclude(group = "org.json", module = "json")
    }

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.4.3")

    implementation("com.google.dagger:dagger:2.35.1")
    kapt("com.google.dagger:dagger-compiler:2.35.1")

    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("androidx.recyclerview:recyclerview:1.2.0")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")
    implementation("com.google.android.material:material:1.3.0")

    implementation("androidx.room:room-runtime:2.2.6")
    implementation("androidx.room:room-ktx:2.2.6")
    kapt("androidx.room:room-compiler:2.2.6")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.3")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("com.squareup.picasso:picasso:2.8")

    "fullImplementation"("com.google.android.gms:play-services-location:18.0.0")
    "fullImplementation"("com.google.firebase:firebase-core:19.0.0")
    "fullImplementation"("com.google.firebase:firebase-iid:21.1.0")
    "fullImplementation"("com.google.firebase:firebase-messaging:22.0.0")
    "fullImplementation"("io.sentry:sentry-android:4.3.0")
    "fullImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.4.3")

    implementation("androidx.work:work-runtime-ktx:2.5.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.webkit:webkit:1.4.0")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:2.0.8")
    testImplementation("org.spekframework.spek2:spek-runner-junit5:2.0.8")
    testImplementation("org.assertj:assertj-core:3.13.2")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.4.3")
    testImplementation("org.altbeacon:android-beacon-library:2+")

    implementation("com.google.android.exoplayer:exoplayer-core:2.14.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.14.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.14.1")
    implementation("com.google.android.exoplayer:extension-cronet:2.14.1")
}

// Disable to fix memory leak and be compatible with the configuration cache.
configure<GoogleServicesPluginConfig> {
    disableVersionCheck = true
}
