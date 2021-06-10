import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jlleitschuh.gradle.ktlint") version "10.0.0"
    id("com.github.ben-manes.versions") version "0.38.0"
}

buildscript {
    repositories {
        google()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
    dependencies {
        classpath(Config.Plugin.android)
        classpath(Config.Plugin.kotlin)
        classpath(Config.Plugin.google)
        classpath(Config.Plugin.appDistribution)
        classpath(Config.Plugin.androidJunit5)
        classpath(Config.Plugin.gpp)
    }
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    tasks.withType<KotlinCompile>().configureEach {
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
