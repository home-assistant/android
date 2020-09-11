plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    compileSdkVersion(Config.Android.compileSdk)

    defaultConfig {
        minSdkVersion(Config.Android.minSdk)
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
