import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.compose.compiler)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")

val keystoreProperties = Properties()

keystoreProperties.load(FileInputStream(keystorePropertiesFile))

android {
    namespace = "io.shpro.companion.android"

    compileSdk = libs.versions.androidSdk.compile.get().toInt()

    defaultConfig {
        applicationId = "io.shpro.companion.android"
        minSdk = libs.versions.androidSdk.wear.min.get().toInt()
        targetSdk = libs.versions.androidSdk.wear.target.get().toInt()

        versionName = project.version.toString()
        // We add 1 because the app and wear versions need to have different version codes.
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1) + 1
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
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

    kotlinOptions {
        jvmTarget = libs.versions.javaVersion.get()
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility(libs.versions.javaVersion.get())
        targetCompatibility(libs.versions.javaVersion.get())
    }

    lint {
        disable += "MissingTranslation"
    }
}

dependencies {
    implementation(project(":common"))

    coreLibraryDesugaring(libs.tools.desugar.jdk)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.material)

    implementation(libs.wear)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.wear.input)
    implementation(libs.wear.remote.interactions)
    implementation(libs.wear.phone.interactions)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.jackson.module.kotlin)
    implementation(libs.okhttp)

    implementation(libs.iconics.core)
    implementation(libs.appcompat)
    implementation(libs.community.material.typeface)
    implementation(libs.iconics.compose)

    implementation(libs.activity.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.uiTooling)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.tooling)

    implementation(libs.guava)
    implementation(libs.bundles.wear.tiles)

    implementation(libs.androidx.watchface.complications.data.source.ktx)

    implementation(libs.androidx.health.services.client)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
