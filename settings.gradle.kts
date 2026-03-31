include(":common", ":app", ":wear", ":automotive", ":testing-unit", ":lint", ":microfrontend")

rootProject.name = "home-assistant-android"

includeBuild("build-logic")

pluginManagement {
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

plugins {
    // So we can't reach the libs.plugins.* aliases from here so we need to declare them the old way...
    id("org.ajoberstar.reckon.settings") version "2.0.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

extensions.configure<org.ajoberstar.reckon.gradle.ReckonExtension>("reckon") {
    val isCiBuild = providers.environmentVariable("CI").isPresent

    setDefaultInferredScope("patch")
    if (!isCiBuild) {
        // Use a snapshot version scheme with Reckon when not running in CI, which allows caching to
        // improve performance. Background: https://github.com/home-assistant/android/issues/5220.
        snapshots()
    } else {
        stages("beta", "final")
    }
    setScopeCalc { java.util.Optional.of(org.ajoberstar.reckon.core.Scope.PATCH) }
    setStageCalc(calcStageFromProp())
    setTagWriter { it.toString() }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("org\\.chromium.*")
            }
        }
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}
