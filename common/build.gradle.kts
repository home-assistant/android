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
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.media)

    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    api(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    api(libs.androidx.work.runtime.ktx)

    // TODO should not expose retrofit outside of common https://github.com/home-assistant/android/issues/5421
    api(platform(libs.retrofit.bom))
    api(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.android)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.android.beacon.library)

    implementation(libs.iconics.core)
    implementation(libs.community.material.typeface)

    implementation(libs.emojiJava) {
        exclude(group = "org.json", module = "json")
    }

    androidTestImplementation(libs.bundles.androidx.test)

    // This fix an issue: provided Metadata instance has version 2.1.0, while maximum supported version is 2.0.0. To support newer versions, update the kotlinx-metadata-jvm library
    lintChecks(libs.androidx.runtime.lint)
    implementation(platform(libs.compose.bom))
}
