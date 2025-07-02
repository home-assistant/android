plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.homeassistant.companion.android.onboarding"
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    screenshotTests {
        imageDifferenceThreshold = 0.00025f // 0.025%
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.core.splashscreen)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.uiTooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.hilt.navigation.compose)

    lintChecks(libs.compose.lint.checks)
}
