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
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(Config.Dependency.Kotlin.core)
    implementation(Config.Dependency.Kotlin.coroutines)

    implementation(Config.Dependency.Google.dagger)
    kapt(Config.Dependency.Google.daggerCompiler)

    implementation(Config.Dependency.Square.retrofit)
}
