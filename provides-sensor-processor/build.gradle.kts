import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
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
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // KSP2 driver used to run the processor end-to-end in tests without a third-party
    // compile-testing harness (the project pins KSP, so we reuse the same version here).
    testImplementation(libs.ksp.symbol.processing.aa.embeddable)
    testImplementation(libs.ksp.symbol.processing.common.deps)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
