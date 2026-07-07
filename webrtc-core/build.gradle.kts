plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
}

android {
    namespace = "io.homeassistant.companion.android.webrtc.core"
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)

    // This module is the only one allowed to depend on libwebrtc directly so the artifact can be
    // swapped without touching consumers. `api` because VideoSink is part of the public player
    // interfaces used by renderers.
    api(libs.webrtc.sdk)

    testImplementation(libs.junit.jupiter.params)
}
