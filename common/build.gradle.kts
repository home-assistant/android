import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    compileSdkVersion(Config.Android.compileSdk)

    defaultConfig {
        minSdkVersion(Config.Android.minSdk)

        javaCompileOptions {
            annotationProcessorOptions {
                arguments = mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "dagger.gradle.incremental" to "true"
                )
            }
        }
    }
}


tasks.withType<KotlinCompile>().all {
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(Config.Dependency.Kotlin.core)
    implementation(Config.Dependency.Kotlin.coroutines)

    implementation(Config.Dependency.AndroidX.room)
    implementation(Config.Dependency.AndroidX.roomKtx)
    kapt(Config.Dependency.AndroidX.roomCompiler)

    implementation(Config.Dependency.Google.dagger)
    kapt (Config.Dependency.Google.daggerCompiler)

    implementation(Config.Dependency.Square.retrofit)
}
