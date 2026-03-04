import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.gradle.api.attributes.Bundling
import org.gradle.api.tasks.JavaExec
import java.io.File

plugins {
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
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    ktlint {
        android.set(true)
        reporters {
            reporter(ReporterType.SARIF)
            reporter(ReporterType.PLAIN)
        }
    }

    // AGP 9 built-in Kotlin no longer applies `org.jetbrains.kotlin.android`,
    // so we run ktlint CLI on `src/**/*.kt` explicitly and hook it into ktlint tasks.
    val kotlinSourcePaths = fileTree("src") {
        include("**/*.kt")
    }.files.map { it.absolutePath.replace(File.separatorChar, '/') }.sorted()

    val ktlintCliVersion = rootProject.libs.versions.ktlint.cli.get()
    val ktlintCliClasspath = configurations.detachedConfiguration(
        dependencies.create("com.pinterest.ktlint:ktlint-cli:$ktlintCliVersion"),
    ).apply {
        attributes.attribute(
            Bundling.BUNDLING_ATTRIBUTE,
            objects.named(Bundling.SHADOWED),
        )
    }

    fun registerKtlintCliTask(taskName: String, format: Boolean, withSarifReport: Boolean) =
        tasks.register<JavaExec>(taskName) {
            group = if (format) "formatting" else "verification"
            classpath = ktlintCliClasspath
            mainClass.set("com.pinterest.ktlint.Main")

            if (format) {
                args("-F")
            }
            args("--relative")
            if (withSarifReport) {
                val reportPath = layout.buildDirectory.file("reports/ktlint/$taskName/$taskName.sarif").get().asFile.absolutePath
                args("--reporter=sarif,output=$reportPath")
            }
            args(kotlinSourcePaths)
            onlyIf { kotlinSourcePaths.isNotEmpty() }
        }

    val ktlintBuiltInKotlinSourceCheck = registerKtlintCliTask(
        taskName = "ktlintBuiltInKotlinSourceCheck",
        format = false,
        withSarifReport = true,
    )
    val ktlintBuiltInKotlinSourceFormat = registerKtlintCliTask(
        taskName = "ktlintBuiltInKotlinSourceFormat",
        format = true,
        withSarifReport = false,
    )

    tasks.named("ktlintCheck") {
        dependsOn(ktlintBuiltInKotlinSourceCheck)
    }
    tasks.named("ktlintFormat") {
        dependsOn(ktlintBuiltInKotlinSourceFormat)
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
