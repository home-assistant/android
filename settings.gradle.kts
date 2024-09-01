
include(":common", ":app", ":wear", ":automotive")

rootProject.name = "home-assistant-android"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // So we can't reach the libs.plugins.* aliases from here so we need to declare them the old way...
    id("org.ajoberstar.reckon.settings").version("0.18.3")
}

reckon {
    setDefaultInferredScope("patch")
    stages("beta", "final")
    setScopeCalc { java.util.Optional.of(org.ajoberstar.reckon.core.Scope.PATCH) }
    setStageCalc(calcStageFromProp())
    setTagWriter { it.toString() }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
        maven("https://plugins.jetbrains.com/plugin/10766-database-debugger")
    }
}
