import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.ktlint)
}

buildscript {
    repositories {
        google()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.android.plugin)
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.google.services)
        classpath(libs.firebase.appdistribution.gradle)
        classpath(libs.android.junit5)
        classpath(libs.play.publisher)
        classpath(libs.hilt.android.gradle.plugin)
    }
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "11"
        }
    }
}

gradle.projectsEvaluated {
    project(":app").tasks.matching { it.name.startsWith("publish") }.configureEach {
        mustRunAfter(project(":wear").tasks.matching { it.name.startsWith("publish") })
    }
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
