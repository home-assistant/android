plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}

val homeAssistantAndroidPushUrl: String by project
val homeAssistantAndroidRateLimitUrl: String by project

android {
    compileSdkVersion(30)

    defaultConfig {
        minSdkVersion(21)
        buildConfigField("String", "PUSH_URL", "\"$homeAssistantAndroidPushUrl\"")
        buildConfigField("String", "RATE_LIMIT_URL", "\"$homeAssistantAndroidRateLimitUrl\"")
        versionName = System.getenv("VERSION") ?: "LOCAL"
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        buildConfigField("String", "VERSION_NAME", "\"$versionName-$versionCode\"")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.5.21")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")

    implementation("com.google.dagger:dagger:2.38")
    kapt("com.google.dagger:dagger-compiler:2.38")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.4")
    implementation("org.altbeacon:android-beacon-library:2.18")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.1")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:2.0.8")
    testImplementation("org.spekframework.spek2:spek-runner-junit5:2.0.15")
    testImplementation("org.assertj:assertj-core:3.13.2")
    testImplementation("io.mockk:mockk:1.12.0")
}
