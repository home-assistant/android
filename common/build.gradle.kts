plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}

val homeAssistantAndroidPushUrl: String by project
val homeAssistantAndroidRateLimitUrl: String by project

android {
    compileSdkVersion(Config.Android.compileSdk)

    defaultConfig {
        minSdkVersion(Config.Android.minSdk)
        buildConfigField("String", "PUSH_URL", "\"$homeAssistantAndroidPushUrl\"")
        buildConfigField("String", "RATE_LIMIT_URL", "\"$homeAssistantAndroidRateLimitUrl\"")
        versionName = System.getenv("VERSION") ?: "LOCAL"
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        buildConfigField("String", "VERSION_NAME", "\"$versionName-$versionCode\"")
    }
}

dependencies {
    implementation(Config.Dependency.Kotlin.core)
    implementation(Config.Dependency.Kotlin.reflect)
    implementation(Config.Dependency.Kotlin.coroutines)

    implementation(Config.Dependency.Google.dagger)
    kapt(Config.Dependency.Google.daggerCompiler)

    implementation(Config.Dependency.Square.retrofit)
    implementation(Config.Dependency.Square.retrofitJacksonConverter)
    implementation(Config.Dependency.Square.okhttp)
    implementation(Config.Dependency.Square.okhttpInterceptor)
    implementation(Config.Dependency.Misc.jackson)
    implementation(Config.Dependency.Misc.altBeacon)
    testImplementation(Config.Dependency.Square.okhttpMockServer)
    testImplementation(Config.Dependency.Testing.spek2Jvm)
    testRuntimeOnly(Config.Dependency.Testing.spek2JUnit)
    testImplementation(Config.Dependency.Testing.assertJ)
    testImplementation(Config.Dependency.Testing.mockk)
}
