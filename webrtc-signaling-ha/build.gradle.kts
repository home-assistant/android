plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
}

android {
    namespace = "io.homeassistant.companion.android.webrtc.signaling"
}

dependencies {
    api(project(":webrtc-core"))
    implementation(project(":common"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
}
