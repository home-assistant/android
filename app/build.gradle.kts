import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig
import java.text.SimpleDateFormat


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
        targetSdk = 33

        versionName = getVersionName()
        versionCode = getVersionCode()

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
        kotlinCompilerExtensionVersion = "1.3.2"
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

//    val NESTOR_KEYSTORE_PASSWORD: String by project
//    val NESTOR_KEYSTORE_ALIAS: String by project
//    val AMAP_KEY: String by project
    val NESTOR_KEYSTORE_PASSWORD = System.getenv("NESTOR_KEYSTORE_PASSWORD")
    val NESTOR_KEYSTORE_ALIAS = System.getenv("NESTOR_KEYSTORE_ALIAS")
    val AMAP_KEY = System.getenv("AMAP_KEY")
    val PGY_API_KEY = System.getenv("PGY_API_KEY")

    signingConfigs {
        create("release") {
            storeFile = file("../nestor.keystore")
            storePassword = NESTOR_KEYSTORE_PASSWORD
            keyAlias = NESTOR_KEYSTORE_ALIAS
            keyPassword = NESTOR_KEYSTORE_PASSWORD
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        named("debug").configure {
            // applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["amapkey"] = AMAP_KEY
            manifestPlaceholders["pgy_api_key"] = PGY_API_KEY
        }
        named("release").configure {
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["amapkey"] = AMAP_KEY
            manifestPlaceholders["pgy_api_key"] = PGY_API_KEY
        }
    }
    flavorDimensions.add("version")
    productFlavors {
//        create("minimal") {
//            applicationIdSuffix = ".minimal"
//            versionNameSuffix = "-minimal"
//        }
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
//        register("minimal") {
//            enabled.set(false)
//        }
        register("full") {
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

    implementation("com.github.Dimezis:BlurView:version-1.6.6")
    implementation("org.altbeacon:android-beacon-library:2.19.5")
    implementation("com.maltaisn:icondialog:3.3.0")
    implementation("com.maltaisn:iconpack-community-material:5.3.45")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    "fullImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")

    implementation("com.google.dagger:hilt-android:2.44.2")
    kapt("com.google.dagger:hilt-android-compiler:2.44.2")

    implementation("androidx.appcompat:appcompat:1.6.0-rc01")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("com.google.android.material:material:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.5.5")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.5")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.picasso:picasso:2.8")

    "fullImplementation"("com.google.android.gms:play-services-location:21.0.1")
    "fullImplementation"("com.google.android.gms:play-services-home:16.0.0")
    "fullImplementation"(platform("com.google.firebase:firebase-bom:31.1.1"))
    "fullImplementation"("com.google.firebase:firebase-messaging")
    "fullImplementation"("io.sentry:sentry-android:6.12.1")
    "fullImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")
    "fullImplementation"("com.google.android.gms:play-services-wearable:18.0.0")
    "fullImplementation"("androidx.wear:wear-remote-interactions:1.0.0")
    "fullImplementation"("com.amap.api:location:6.1.0")
    implementation("com.tencent.bugly:crashreport:latest.release")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.webkit:webkit:1.5.0")

    implementation("com.google.android.exoplayer:exoplayer-core:2.18.2")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.18.2")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.18.2")
    "fullImplementation"("com.google.android.exoplayer:extension-cronet:2.18.2")

    implementation(platform("androidx.compose:compose-bom:2022.10.00"))
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.compiler:compiler:1.3.2")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.6.1")
    implementation("androidx.navigation:navigation-compose:2.5.3")
    implementation("com.google.accompanist:accompanist-themeadapter-material:0.28.0")

    implementation("com.mikepenz:iconics-core:5.4.0")
    implementation("com.mikepenz:iconics-compose:5.4.0")
    implementation("com.mikepenz:community-material-typeface:7.0.96.0-kotlin@aar")

    "fullImplementation"("org.burnoutcrew.composereorderable:reorderable:0.9.6")
    implementation("com.github.AppDevNext:ChangeLog:3.4")

    "fullImplementation"("androidx.car.app:app:1.3.0-rc01")
}

// Disable to fix memory leak and be compatible with the configuration cache.
configure<GoogleServicesPluginConfig> {
    disableVersionCheck = true
}

fun getVersionCode(): Int {
    val time = System.currentTimeMillis()
    return (time / 1000).toInt()
}

fun getVersionName(): String {
    return "v" + SimpleDateFormat("yyyy-MM-dd").format(System.currentTimeMillis())
}
