plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val homeAssistantAndroidPushUrl: String by project
val homeAssistantAndroidRateLimitUrl: String by project

val versionName = project.version.toString()
val versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

android {
    namespace = "io.homeassistant.companion.android.common"

    compileSdk = libs.versions.androidSdk.compile.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidSdk.min.get().toInt()
        buildConfigField("String", "PUSH_URL", "\"$homeAssistantAndroidPushUrl\"")
        buildConfigField("String", "RATE_LIMIT_URL", "\"$homeAssistantAndroidRateLimitUrl\"")
        buildConfigField("String", "VERSION_NAME", "\"$versionName-$versionCode\"")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = libs.versions.javaVersion.get()
    }

    compileOptions {
        sourceCompatibility(libs.versions.javaVersion.get())
        targetCompatibility(libs.versions.javaVersion.get())
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
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    implementation(libs.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    api(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    api(libs.androidx.work.runtime.ktx)

    api(libs.retrofit)
    implementation(libs.converter.jackson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.android.beacon.library)

    implementation(libs.iconics.core)
    implementation(libs.community.material.typeface)

    implementation(libs.emojiJava) {
        exclude(group = "org.json", module = "json")
    }
}
