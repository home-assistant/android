import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.homeassistant.companion.android.testing.unit"

    compileSdk = libs.versions.androidSdk.compile.get().toInt()

    compileOptions {
        sourceCompatibility(libs.versions.javaVersion.get())
        targetCompatibility(libs.versions.javaVersion.get())
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaVersion.get()))
    }
}

dependencies {
    implementation(libs.timber)

    implementation(platform(libs.junit.bom))
    implementation(libs.junit.jupiter)
    implementation(libs.junit.vintage.engine)
    implementation(libs.kotlinx.coroutines.test)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.androidx.compose.ui.test)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.wearable)
}
