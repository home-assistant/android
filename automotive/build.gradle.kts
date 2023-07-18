import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
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
        minSdk = 29
        targetSdk = 33

        versionName = project.version.toString()
        // We add 2 because the app, wear (+1) and automotive versions need to have different version codes.
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1) + 3

        manifestPlaceholders["sentryRelease"] = "$applicationId@$versionName"
        manifestPlaceholders["sentryDsn"] = System.getenv("SENTRY_DSN") ?: ""

        bundle {
            language {
                enableSplit = false
            }
        }
    }

    sourceSets {
        getByName("main") {
            java {
                srcDirs("../app/src/main/java")
            }
            assets {
                srcDirs("../app/src/main/assets")
            }
            res {
                srcDirs("../app/src/main/res")
            }
        }
        create("full") {
            java {
                srcDirs("../app/src/full/java")
            }
            res {
                srcDirs("../app/src/full/res")
            }
        }
        create("minimal") {
            java {
                srcDirs("../app/src/minimal/java")
            }
            res {
                srcDirs("../app/src/minimal/res")
            }
        }
        getByName("debug") {
            res {
                srcDirs("../app/src/debug/res")
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

    kotlinOptions {
        jvmTarget = "11"
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
    track.set("automotive:internal")
    resolutionStrategy.set(ResolutionStrategy.IGNORE)
    // We will depend on the wear commit.
    commit.set(true)
}

dependencies {
    implementation(project(":common"))

    implementation("com.github.Dimezis:BlurView:version-1.6.6")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.2")
    "fullImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.2")

    implementation("com.google.dagger:hilt-android:2.46.1")
    kapt("com.google.dagger:hilt-android-compiler:2.47")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.6.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.5")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.picasso:picasso:2.8")

    "fullImplementation"("com.google.android.gms:play-services-location:21.0.1")
    "fullImplementation"("com.google.android.gms:play-services-home:16.0.0")
    "fullImplementation"("com.google.android.gms:play-services-threadnetwork:16.0.0")
    "fullImplementation"(platform("com.google.firebase:firebase-bom:32.1.0"))
    "fullImplementation"("com.google.firebase:firebase-messaging")
    "fullImplementation"("io.sentry:sentry-android:6.25.1")
    "fullImplementation"("com.google.android.gms:play-services-wearable:18.0.0")
    "fullImplementation"("androidx.wear:wear-remote-interactions:1.0.0")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.webkit:webkit:1.7.0")

    implementation("com.google.android.exoplayer:exoplayer-core:2.18.7")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.18.7")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.18.7")
    "fullImplementation"("com.google.android.exoplayer:extension-cronet:2.18.7")
    "minimalImplementation"("com.google.android.exoplayer:extension-cronet:2.18.7") {
        exclude(group = "com.google.android.gms", module = "play-services-cronet")
    }
    "minimalImplementation"("org.chromium.net:cronet-embedded:113.5672.61")

    implementation(platform("androidx.compose:compose-bom:2023.06.01"))
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.compiler:compiler:1.4.8")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.navigation:navigation-compose:2.6.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.30.1")
    implementation("com.google.accompanist:accompanist-themeadapter-material:0.30.1")

    implementation("com.mikepenz:iconics-core:5.4.0")
    implementation("com.mikepenz:iconics-compose:5.4.0")
    implementation("com.mikepenz:community-material-typeface:7.0.96.0-kotlin@aar")

    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")
    implementation("com.github.AppDevNext:ChangeLog:3.4")

    implementation("androidx.car.app:app:1.3.0-rc01")
    implementation("androidx.car.app:app-automotive:1.3.0-rc01")
}

// Disable to fix memory leak and be compatible with the configuration cache.
configure<GoogleServicesPluginConfig> {
    disableVersionCheck = true
}
