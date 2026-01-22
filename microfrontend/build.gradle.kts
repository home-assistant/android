plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
}

android {
    namespace = "io.homeassistant.companion.android.microfrontend"
    ndkVersion = libs.versions.andrdoiNdk.get()

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                // Enable flexible page sizes for Android 15+ compatibility
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64") // Limit to LiteRt arch supported for Android
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
}

dependencies {
    androidTestImplementation(libs.bundles.androidx.test)
}
