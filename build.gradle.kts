import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
}

buildscript {
    repositories {
        google()
        jcenter()
        maven(url = "https://maven.fabric.io/public")
        maven(url = "https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.6.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72")
        classpath("com.google.gms:google-services:4.3.3")
        classpath("com.google.firebase:firebase-appdistribution-gradle:1.4.0")
        classpath("io.fabric.tools:gradle:1.31.2")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:9.2.1")
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        maven(url = "https://maven.lokalise.co")
    }
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    tasks.withType<KotlinCompile>().configureEach {
        println("Configuring $name in project ${project.name}...")
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}

tasks.register("clean").configure {
    delete("build")
}

ktlint {
    android.set(true)
}