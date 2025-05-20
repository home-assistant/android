plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.homeassistant.companion.android.testing.unit"

    compileSdk = libs.versions.androidSdk.compile.get().toInt()

    kotlinOptions {
        jvmTarget = libs.versions.javaVersion.get()
    }

    compileOptions {
        sourceCompatibility(libs.versions.javaVersion.get())
        targetCompatibility(libs.versions.javaVersion.get())
    }
}

dependencies {
    implementation(libs.timber)

    implementation(platform(libs.junit.bom))
    implementation(libs.junit.jupiter)
}
