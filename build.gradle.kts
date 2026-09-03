import dev.detekt.gradle.Detekt
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

val kotlinVersion = libs.versions.kotlin.get()

plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)

    alias(libs.plugins.aboutlibraries).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.android.lint).apply(false)
    alias(libs.plugins.google.services).apply(false)
    alias(libs.plugins.firebase.appdistribution).apply(false)
    alias(libs.plugins.hilt).apply(false)
    alias(libs.plugins.kotlin.parcelize).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.kotlin.serialization).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.screenshot).apply(false)
}

allprojects {
    // :automotive has no Kotlin sources of its own; it reuses :app's, which are already analyzed
    // there. Don't apply detekt to it at all: even disabled detekt tasks would still compile the
    // module through their task dependencies.
    val reusesAppSources = path == ":automotive"

    if (!reusesAppSources) {
        apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    }
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    // TODO this has been added until https://youtrack.jetbrains.com/issue/KT-87220/Kotlin-Gradle-plugin-resolves-kotlinAbiValidationCompatClasspath-to-newer-beta-Kotlin-artifacts-during-dependency-locking is addressed
    configurations.matching { it.name == "kotlinAbiValidationCompatClasspath" }.configureEach {
        resolutionStrategy.eachDependency {
            if (
                requested.group == "org.jetbrains.kotlin" &&
                requested.name in setOf(
                    "kotlin-build-tools-api",
                    "kotlin-build-tools-cri-impl",
                    "kotlin-build-tools-impl",
                    "kotlin-compiler-embeddable",
                    "kotlin-compiler-runner",
                    "kotlin-daemon-client",
                    "kotlin-daemon-embeddable",
                    "kotlin-script-runtime",
                    "kotlin-stdlib",
                    "kotlin-tooling-core",
                )
            ) {
                useVersion(kotlinVersion)
                because("Keep Kotlin ABI validation tooling aligned with the configured Kotlin version.")
            }
        }
    }

    ktlint {
        android.set(true)
        reporters {
            reporter(ReporterType.SARIF)
            reporter(ReporterType.PLAIN)
        }
        if (reusesAppSources) {
            // Only lint the module's own files (build scripts); the Kotlin sources belong to :app.
            val appDir = rootDir.resolve("app").absolutePath + File.separator
            filter {
                exclude { it.file.absolutePath.startsWith(appDir) }
            }
        }
    }

    if (!reusesAppSources) {
        detekt {
            config.setFrom(rootProject.file(".detekt/detekt.yml"))
            buildUponDefaultConfig = true
            // Debug variants only add debug-only dev tooling on top of what release compiles; analyzing
            // release covers everything that ships and halves the type-resolution work.
            ignoredBuildTypes = listOf("debug")
        }

        tasks.withType<Detekt>().configureEach {
            reports {
                html.required.set(true)
                sarif.required.set(true)
            }
        }
    }

    dependencyLocking {
        lockAllConfigurations()
    }
}

tasks.register("clean") {
    delete("build")
}

tasks.register("alldependencies") {
    setDependsOn(
        project.allprojects.flatMap {
            it.tasks.withType<DependencyReportTask>()
        },
    )
}

tasks.register("versionFile") {
    group = "publishing"
    description = "Writes the project.version to a file version.txt at the root of the project"

    notCompatibleWithConfigurationCache(
        "The version of the project depends on the timestamp of the build and cannot be cached.",
    )

    // Use a provider to avoid capturing script object references
    outputs.file("$projectDir/version.txt")
    // Retrieve the project version here since querying `project` at execution time is unsupported when configuration cache is enabled
    val projectVersion = project.version.toString()

    doLast {
        val versionFile = outputs.files.singleFile
        versionFile.writeText(projectVersion)
        println("Version written to ${versionFile.absolutePath}")
    }
}
