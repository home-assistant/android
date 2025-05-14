import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig

plugins {
    alias(libs.plugins.homeassistant.android.application)
    alias(libs.plugins.homeassistant.android.flavor)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.google.services)
    alias(libs.plugins.homeassistant.android.dependencies)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    useLibrary("android.car")

    defaultConfig {
        manifestPlaceholders["sentryRelease"] = "$applicationId@$versionName"
        manifestPlaceholders["sentryDsn"] = System.getenv("SENTRY_DSN") ?: ""

        bundle {
            language {
                // We want to keep the translations in the final AAB for all the language
                enableSplit = false
            }
        }
    }

    firebaseAppDistribution {
        serviceCredentialsFile = "firebaseAppDistributionServiceCredentialsFile.json"
        releaseNotesFile = "./app/build/outputs/changelogBeta"
        groups = "continuous-deployment"
    }
}

dependencies {
    // Most of the dependencies are coming from the convention plugin to avoid duplication with `:automotive` module.
    "fullImplementation"(libs.car.projected)
}

// Disable to fix memory leak and be compatible with the configuration cache.
configure<GoogleServicesPluginConfig> {
    disableVersionCheck = true
}
