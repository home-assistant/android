import com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig

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
    }

    sourceSets {
        getByName("main") {
            java {
                srcDirs("../app/src/main/java")
            }
            assets {
                srcDirs("../app/src/main/assets")
            }
            res {
                srcDirs("../app/src/main/res")
            }
        }
        getByName("full") {
            java {
                srcDirs("../app/src/full/java")
            }
            res {
                srcDirs("../app/src/full/res")
            }
        }
        getByName("minimal") {
            java {
                srcDirs("../app/src/minimal/java")
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
    }
}

dependencies {
    // Most of the dependencies are coming from the convention plugin to avoid duplication with `:app` module.
    implementation(libs.car.automotive)
}

// Disable to fix memory leak and be compatible with the configuration cache.
configure<GoogleServicesPluginConfig> {
    disableVersionCheck = true
}
