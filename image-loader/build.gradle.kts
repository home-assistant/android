plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
}

android {
    namespace = "io.homeassistant.companion.android.imageloader"
}

dependencies {
    // Coil stays an implementation detail of this module so consumers can only load images through
    // HAImageLoader, which waits for the app's OkHttpClient before issuing any request.
    implementation(libs.bundles.coil)

    implementation(libs.kotlinx.coroutines.core)

    api(platform(libs.okhttp.bom))
    api(libs.okhttp.android)
}
