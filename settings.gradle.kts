include(":common", ":app")

rootProject.name = "home-assistant-android"

plugins {
    id("com.gradle.enterprise").version("3.4.1")
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishAlways()
    }
}
