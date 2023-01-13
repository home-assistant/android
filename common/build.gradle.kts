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
    namespace = "io.homeassistant.companion.android.common"

    compileSdk = 33

    defaultConfig {
        minSdk = 21
        buildConfigField("String", "PUSH_URL", "\"$homeAssistantAndroidPushUrl\"")
        buildConfigField("String", "RATE_LIMIT_URL", "\"$homeAssistantAndroidRateLimitUrl\"")
        buildConfigField("String", "VERSION_NAME", "\"$versionName-$versionCode\"")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments(
                    mapOf(
                        "room.incremental" to "true",
                        "room.schemaLocation" to "$projectDir/schemas"
                    )
                )
            }
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }

    lint {
        abortOnError = false
        disable += "MissingTranslation"
    }

    kapt {
        correctErrorTypes = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("com.google.dagger:hilt-android:2.44.2")
    kapt("com.google.dagger:hilt-android-compiler:2.44.2")

    api("androidx.room:room-runtime:2.4.3")
    api("androidx.room:room-ktx:2.4.3")
    kapt("androidx.room:room-compiler:2.4.3")

    api("androidx.work:work-runtime-ktx:2.7.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("org.altbeacon:android-beacon-library:2.19.5")

    implementation("com.mikepenz:iconics-core:5.4.0")
    implementation("com.mikepenz:community-material-typeface:7.0.96.0-kotlin@aar")

    implementation("com.vdurmont:emoji-java:5.1.1") {
        exclude(group = "org.json", module = "json")
    }
}
