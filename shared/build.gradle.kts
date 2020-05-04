plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-android-extensions")
    id("kotlin-kapt")
}

android {
    compileSdkVersion(Config.Android.compileSdk)

    defaultConfig {
        minSdkVersion(Config.Android.minSdk)
    }

    lintOptions {
        disable("MissingTranslation")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":data"))
    implementation(project(":domain"))

    implementation(Config.Dependency.Kotlin.core)
    implementation(Config.Dependency.Kotlin.coroutines)
    implementation(Config.Dependency.Kotlin.coroutinesAndroid)

    implementation(Config.Dependency.AndroidX.preference)
    implementation(Config.Dependency.AndroidX.workManager)

    implementation(Config.Dependency.Google.material)
    implementation(Config.Dependency.Google.dagger)
    kapt (Config.Dependency.Google.daggerCompiler)
    
    implementation(Config.Dependency.Play.location)
    
    implementation(Config.Dependency.Firebase.core)
    implementation(Config.Dependency.Firebase.iid)
    api(Config.Dependency.Firebase.messaging)

    implementation(Config.Dependency.Square.okhttp)
}