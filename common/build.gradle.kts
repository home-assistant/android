plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

val homeAssistantAndroidPushUrl: String by project
val homeAssistantAndroidRateLimitUrl: String by project

val versionName = System.getenv("VERSION") ?: "LOCAL"
val versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

android {
    compileSdk = 31

    defaultConfig {
        minSdk = 21
        buildConfigField("String", "PUSH_URL", "\"$homeAssistantAndroidPushUrl\"")
        buildConfigField("String", "RATE_LIMIT_URL", "\"$homeAssistantAndroidRateLimitUrl\"")
        buildConfigField("String", "VERSION_NAME", "\"$versionName-$versionCode\"")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")

    implementation("com.google.dagger:hilt-android:2.40.1")
    kapt("com.google.dagger:hilt-android-compiler:2.40.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.2")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")
    implementation("org.altbeacon:android-beacon-library:2.19.3")
}
