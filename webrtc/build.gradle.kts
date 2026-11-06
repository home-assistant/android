plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
}

android {
    namespace = "io.homeassistant.companion.android.webrtc"
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.webrtc.sdk)

    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.androidx.test.core)
}
