plugins {
    alias(libs.plugins.homeassistant.android.application)
    alias(libs.plugins.homeassistant.android.flavor)
    alias(libs.plugins.google.services)
    alias(libs.plugins.homeassistant.android.dependencies)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    useLibrary("android.car")

    defaultConfig {
        // Override minSDK since automotive bellow 29 does not exist
        minSdk = libs.versions.androidSdk.automotive.min.get().toInt()

        // We add 3 because the app, wear (+1) and automotive versions need to have different version codes.
        versionCode = 3 + checkNotNull(versionCode) { "Did you forget to apply the convention plugin that set the version code?" }

        manifestPlaceholders["sentryRelease"] = "$applicationId@$versionName"
        manifestPlaceholders["sentryDsn"] = System.getenv("SENTRY_DSN") ?: ""

        bundle {
            language {
                enableSplit = false
            }
        }
        lint {
            // We disable the lint tasks for release only for automotive because the baseline cannot be parsed in release builds due to the fact that we share sources between app and automotive.
            // It is not an issue since we still check the :app module and the code is exactly the same.
            checkReleaseBuilds = false

            // Until we fully migrate to Material3 this lint issue is too verbose https://github.com/home-assistant/android/issues/5420
            disable += listOf("UsingMaterialAndMaterial3Libraries")
        }
    }

    sourceSets {
        getByName("main") {
            kotlin {
                srcDirs("../app/src/main/kotlin")
            }
            assets {
                srcDirs("../app/src/main/assets")
            }
            res {
                srcDirs("../app/src/main/res")
            }
        }
        getByName("full") {
            kotlin {
                srcDirs("../app/src/full/kotlin")
            }
            res {
                srcDirs("../app/src/full/res")
            }
        }
        getByName("minimal") {
            kotlin {
                srcDirs("../app/src/minimal/kotlin")
            }
            res {
                srcDirs("../app/src/minimal/res")
            }
        }
        getByName("debug") {
            res {
                srcDirs("../app/src/debug/res")
            }
        }
        getByName("androidTest") {
            kotlin {
                srcDirs("../app/src/androidTest/kotlin")
            }
            assets {
                srcDirs("../app/src/androidTest/assets")
            }
            res {
                srcDirs("../app/src/androidTest/res")
            }
        }
    }
}

dependencies {
    // Most of the dependencies are coming from the convention plugin to avoid duplication with `:app` module.
    implementation(libs.car.automotive)
}

// Disable to fix memory leak and be compatible with the configuration cache.
googleServices {
    disableVersionCheck = true
}
