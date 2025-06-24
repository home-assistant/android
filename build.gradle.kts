import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.ktlint)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.android.lint).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.google.services).apply(false)
    alias(libs.plugins.firebase.appdistribution).apply(false)
    alias(libs.plugins.hilt).apply(false)
    alias(libs.plugins.kotlin.parcelize).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.kotlin.serialization).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
}

allprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    ktlint {
        android.set(true)
        reporters {
            reporter(ReporterType.SARIF)
            reporter(ReporterType.PLAIN)
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

    notCompatibleWithConfigurationCache("The version of the project depends on the timestamp of the build and cannot be cached.")

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
