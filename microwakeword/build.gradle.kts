plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
}

android {
    namespace = "io.homeassistant.companion.android.microwakeword"
    ndkVersion = libs.versions.androidNdk.get()

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                // Enable flexible page sizes for Android 15+ compatibility
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    packaging {
        jniLibs {
            // Required for HWASan wrap.sh to be included uncompressed in the test APK
            useLegacyPackaging = true
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

// If we ever add unit test to this module we could remove this block
tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}
