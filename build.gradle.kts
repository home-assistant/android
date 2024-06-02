plugins {
    alias(libs.plugins.ktlint)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.google.services).apply(false)
    alias(libs.plugins.firebase.appdistribution).apply(false)
    alias(libs.plugins.hilt).apply(false)
    alias(libs.plugins.kotlin.parcelize).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
}

allprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
}

tasks.register("clean").configure {
    delete("build")
}

ktlint {
    android.set(true)
}

tasks.register("versionFile").configure {
    group = "publishing"
    doLast {
        File(projectDir, "version.txt").writeText(project.version.toString())
    }
}
