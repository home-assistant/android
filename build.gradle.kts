import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
    id("com.github.ben-manes.versions") version "0.42.0"
}

buildscript {
    repositories {
        google()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.31")
        classpath("com.google.gms:google-services:4.3.10")
        classpath("com.google.firebase:firebase-appdistribution-gradle:3.0.0")
        classpath("de.mannodermaus.gradle.plugins:android-junit5:1.8.2.0")
        classpath("com.github.triplet.gradle:play-publisher:3.7.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.40.5")
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
