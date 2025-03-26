import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

group = "io.homeassistant.companion.android.buildlogic"

allprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    ktlint {
        android.set(true)
        reporters {
            reporter(ReporterType.SARIF)
            reporter(ReporterType.PLAIN)
        }

        // Fix for an implicit_dependency after bumping typesafe-conventions to 0.5.1
        // https://github.com/radoslaw-panuszewski/typesafe-conventions-gradle-plugin/issues/34
        filter {
            exclude { it.file.path.contains("build${File.separator}generated-sources") }
        }
    }
}

// Configure the build-logic plugins to target JDK 17 and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.homeassistant.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationDependencies") {
            id = libs.plugins.homeassistant.android.dependencies.get().pluginId
            implementationClass = "AndroidApplicationDependenciesConventionPlugin"
        }
        register("androidApplicationFullMinimalFlavor") {
            id = libs.plugins.homeassistant.android.flavor.get().pluginId
            implementationClass = "AndroidFullMinimalFlavorConventionPlugin"
        }
        register("androidCommon") {
            id = libs.plugins.homeassistant.android.common.get().pluginId
            implementationClass = "AndroidCommonConventionPlugin"
        }
    }
}
