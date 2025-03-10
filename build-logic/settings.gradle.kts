pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

plugins {
    // This is used to be able to use version catalog within this project (`libs` variable)
    // because of that we can't have the version of this plugin in the version catalog.
    id("dev.panuszewski.typesafe-conventions") version "0.4.1"
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "build-logic"
include(":convention")
