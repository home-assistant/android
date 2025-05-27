import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm")
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.javaVersion.get())
    }
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.lint.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.lint.tests)
    testImplementation(libs.lint.checks)
}
