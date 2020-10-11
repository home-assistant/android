import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}

fun gradleLocalProperties(projectRootDir: File): java.util.Properties {
    val properties = Properties()
    val localProperties = File(projectRootDir, "local.properties")
    if (localProperties.isFile) {
        InputStreamReader(FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
    }
    return properties
}

val pushUrl: String = gradleLocalProperties(rootDir).getProperty("push_url")
    ?: "https://mobile-apps.home-assistant.io/api/sendPush/android/v1"
val rateLimitUrl: String = gradleLocalProperties(rootDir).getProperty("rate_limit_url")
    ?: "https://mobile-apps.home-assistant.io/api/checkRateLimits"

android {
    compileSdkVersion(Config.Android.compileSdk)

    defaultConfig {
        minSdkVersion(Config.Android.minSdk)
        buildConfigField("String", "PUSH_URL", "\"$pushUrl\"")
        buildConfigField("String", "RATE_LIMIT_URL", "\"$rateLimitUrl\"")
    }
}

dependencies {
    implementation(Config.Dependency.Kotlin.core)
    implementation(Config.Dependency.Kotlin.coroutines)

    implementation(Config.Dependency.Google.dagger)
    kapt(Config.Dependency.Google.daggerCompiler)

    implementation(Config.Dependency.Square.retrofit)
    implementation(Config.Dependency.Square.retrofitJacksonConverter)
    implementation(Config.Dependency.Square.okhttp)
    implementation(Config.Dependency.Square.okhttpInterceptor)
    implementation(Config.Dependency.Misc.jackson)

    testImplementation(Config.Dependency.Square.okhttpMockServer)
    testImplementation(Config.Dependency.Testing.spek2Jvm)
    testRuntimeOnly(Config.Dependency.Testing.spek2JUnit)
    testImplementation(Config.Dependency.Testing.assertJ)
    testImplementation(Config.Dependency.Testing.mockk)
}
