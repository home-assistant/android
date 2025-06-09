plugins {
    alias(libs.plugins.homeassistant.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.screenshot)
}

android {
    defaultConfig {
        minSdk = libs.versions.androidSdk.wear.min.get().toInt()
        targetSdk = libs.versions.androidSdk.wear.target.get().toInt()

        versionName = project.version.toString()
        // We add 1 because the app and wear versions need to have different version codes.
        versionCode = 1 + checkNotNull(versionCode) { "Did you forget to apply the convention plugin that set the version code?" }
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    testOptions {
        screenshotTests {
            imageDifferenceThreshold = 0.00025f // 0.025%
        }
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
    screenshotTestImplementation(libs.compose.uiTooling)
    screenshotTestImplementation(libs.screenshot.validation.api)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.bundles.androidx.compose.ui.test)
}
