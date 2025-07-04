include(":common", ":app", ":wear", ":automotive", ":testing-unit", ":lint", ":onboarding")

rootProject.name = "home-assistant-android"

pluginManagement {
    includeBuild("build-logic")
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
    id("org.ajoberstar.reckon.settings").version("0.19.2")
}

reckon {
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
