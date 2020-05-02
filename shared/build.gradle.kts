plugins {
    id("com.android.library")
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
    implementation(Config.Dependency.Google.material)
}