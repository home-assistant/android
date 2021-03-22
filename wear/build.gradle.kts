plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-android-extensions")
}

android {
    compileSdkVersion(Config.Android.compileSdk)

    defaultConfig {
        applicationId = "io.homeassistant.companion.android"
        minSdkVersion(Config.Android.minSdkWear)
        targetSdkVersion(Config.Android.targetSdk)

        versionName = System.getenv("VERSION") ?: "LOCAL"
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

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

    signingConfigs {
        create("release") {
            storeFile = file("../nestor.keystore")
            storePassword = System.getenv("NESTOR_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("NESTOR_KEYSTORE_ALIAS")
            keyPassword = System.getenv("NESTOR_KEYSTORE_PASSWORD")
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
    kotlinOptions {
        jvmTarget = "1.8"
    }

    lintOptions {
        disable("MissingTranslation")
    }
}

dependencies {
    implementation(project(":common"))

    implementation(Config.Dependency.Google.material)

    implementation(Config.Dependency.AndroidX.wear)
    implementation(Config.Dependency.Google.wearableSupport)
    compileOnly(Config.Dependency.Google.wearable)
}
