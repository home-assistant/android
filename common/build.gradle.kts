plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
}

val homeAssistantAndroidPushUrl: String by project
val homeAssistantAndroidRateLimitUrl: String by project

val versionName = project.version.toString()
val versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

android {
    namespace = "io.homeassistant.companion.android.common"

    defaultConfig {
        buildConfigField("String", "PUSH_URL", "\"$homeAssistantAndroidPushUrl\"")
        buildConfigField("String", "RATE_LIMIT_URL", "\"$homeAssistantAndroidRateLimitUrl\"")
        buildConfigField("String", "VERSION_NAME", "\"$versionName-$versionCode\"")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.media)

    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    api(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    api(libs.androidx.work.runtime.ktx)

    api(libs.retrofit)
    implementation(libs.retrofit.converter.jackson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.android.beacon.library)

    implementation(libs.iconics.core)
    implementation(libs.community.material.typeface)

    implementation(libs.emojiJava) {
        exclude(group = "org.json", module = "json")
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.bundles.androidx.test)
}
