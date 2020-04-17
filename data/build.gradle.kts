plugins {
    id("java")
    id("kotlin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Test> {
    useJUnitPlatform {
        includeEngines("spek2")
    }
}

dependencies {
    implementation(project(":domain"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Config.Dependency.Kotlin.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Config.Dependency.Kotlin.coroutines}")

    implementation("javax.inject:javax.inject:1")

    implementation("com.squareup.retrofit2:retrofit:${Config.Dependency.Square.retrofit}")
    implementation("com.squareup.retrofit2:converter-jackson:${Config.Dependency.Square.retrofit}")
    implementation("com.squareup.okhttp3:okhttp:${Config.Dependency.Square.okhttp}")
    implementation("com.squareup.okhttp3:logging-interceptor:${Config.Dependency.Square.okhttp}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Config.Dependency.Misc.jackson}")

    implementation("org.threeten:threetenbp:${Config.Dependency.Misc.threeTenBp}")

    testImplementation("com.squareup.okhttp3:mockwebserver:${Config.Dependency.Square.okhttp}")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Config.Dependency.Testing.spek2}")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Config.Dependency.Testing.spek2}")
    testImplementation("org.assertj:assertj-core:${Config.Dependency.Testing.assertJ}")
    testImplementation("io.mockk:mockk:${Config.Dependency.Testing.mockk}")
}
