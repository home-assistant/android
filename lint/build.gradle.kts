import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

// JUnit 6 requires JDK 17+ at runtime. We only set targetCompatibility/jvmTarget
// for tests, not sourceCompatibility, because the test source code can still be written
// using the lower Java version features while being compiled to run on JDK 17+.
tasks.named<JavaCompile>("compileTestJava") {
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
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

dependencyLocking {
    // Unlocks configuration since it doesn't work with the lint configuration and doesn't generate the lockfile for the lint targets
    unlockAllConfigurations()
}

tasks.jar {
    manifest.attributes(
        mapOf("Lint-Registry-v2" to "io.homeassistant.lint.LintRegistry"),
    )
}
