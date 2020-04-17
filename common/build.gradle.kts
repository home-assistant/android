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

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Config.Dependency.Kotlin.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Config.Dependency.Kotlin.coroutines}")

    implementation("com.google.dagger:dagger:${Config.Dependency.Google.dagger}")
    kapt ("com.google.dagger:dagger-compiler:${Config.Dependency.Google.dagger}")

    implementation("com.squareup.retrofit2:retrofit:${Config.Dependency.Square.retrofit}")
}
