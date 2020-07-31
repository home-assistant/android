import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
}

buildscript {
    repositories {
        google()
        jcenter()
        maven(url = Config.Repository.gradle)
    }
    dependencies {
        classpath(Config.Plugin.android)
        classpath(Config.Plugin.kotlin)
        classpath(Config.Plugin.google)
        classpath(Config.Plugin.appDistribution)
        classpath(Config.Plugin.ktlint)
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        maven(url = Config.Repository.lokalize)
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
