import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm")
    alias(libs.plugins.android.lint)
}

// These checks run inside the lint tool's own JVM, never on a device, so they follow lint's
// requirement (JDK 17+ since AGP 8) rather than the Android `javaVersion`. Targeting the lower
// Android version makes Gradle reject the lint artifacts, which are published as JDK 17+ only.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.lint.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.lint.tests)
    testImplementation(libs.lint.checks)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
