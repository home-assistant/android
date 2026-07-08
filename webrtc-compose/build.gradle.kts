plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
    alias(libs.plugins.homeassistant.android.compose)
}

android {
    namespace = "io.homeassistant.companion.android.webrtc.compose"
}

dependencies {
    api(project(":webrtc-core"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.androidx.test.core)
}
