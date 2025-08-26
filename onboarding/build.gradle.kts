plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
    alias(libs.plugins.homeassistant.android.compose)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.homeassistant.companion.android.onboarding"

    defaultConfig {
        testInstrumentationRunner = "io.homeassistant.companion.android.CustomTestRunner"
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.kotlin.stdlib)

    implementation(libs.core.splashscreen)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.accompanist.permissions)

    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    implementation(libs.androidx.hilt.navigation.compose)

    lintChecks(libs.compose.lint.checks)

    kspTest(libs.hilt.android.compiler)
    testImplementation(libs.navigation.test)
    testImplementation(libs.hilt.android.testing)

    androidTestImplementation(libs.kotlin.stdlib)
    androidTestImplementation(libs.leakcanary.android.instrumentation)
    androidTestImplementation(libs.bundles.androidx.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
