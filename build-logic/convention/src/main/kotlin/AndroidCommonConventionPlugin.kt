
import io.homeassistant.companion.android.androidConfig
import io.homeassistant.companion.android.getPluginId
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * A convention plugin that applies common configurations to Android modules (Application and Library).
 * This centralizes configuration, preventing duplication across multiple modules.
 *
 * This plugin applies several Gradle plugins that are commonly used in all android modules.
 *
 * After applying this plugin, the configured values can be overridden if necessary. However,
 * if extensive overrides are required, it may indicate that the configuration should be moved
 * out of this convention plugin.
 *
 */
class AndroidCommonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.kotlin.android.getPluginId())
            apply(plugin = libs.plugins.kotlin.serialization.getPluginId())
            apply(plugin = libs.plugins.ksp.getPluginId())
            apply(plugin = libs.plugins.hilt.getPluginId())

            // We create a resources directory to put `robolectric.properties` and set the SDK version
            // inspired from https://github.com/PaulWoitaschek/Voice/commit/55505083dd3c3ecfe7b28192d7e9664ebb066399
            // More info https://github.com/robolectric/robolectric/issues/10103
            val testResourcesDir = file("build/generated/testResources").apply { mkdirs() }

            androidConfig {
                compileSdk = libs.versions.androidSdk.compile.get().toInt()

                defaultConfig {
                    minSdk = libs.versions.androidSdk.min.get().toInt()
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildFeatures {
                    buildConfig = true
                }

                compileOptions {
                    sourceCompatibility(libs.versions.javaVersion.get())
                    targetCompatibility(libs.versions.javaVersion.get())
                    isCoreLibraryDesugaringEnabled = true
                }

                tasks.withType<KotlinCompile>().configureEach {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaVersion.get()))
                    }
                }

                testOptions {
                    unitTests {
                        isReturnDefaultValues = true
                        isIncludeAndroidResources = true
                    }
                }

                sourceSets {
                    named("test") {
                        resources.srcDir(testResourcesDir)
                    }
                }

                tasks.withType<Test> {
                    useJUnitPlatform()
                }

                lint {
                    // Lint task should fail if there are issues so the CI doesn't allow addition of lint issue.
                    abortOnError = true
                    // This is an aggressive settings but it helps keeping the code base out of warnings
                    warningsAsErrors = true
                    // We need to disable MissingTranslation since we use localize and the translation might comes later
                    disable += "MissingTranslation"
                    // This report is used by Github Actions to parse the new issues and report them into the PR.
                    sarifReport = true
                    // Sometimes we need to bypass lint issues we use this file to keep track of them.
                    baseline = file("lint-baseline.xml")
                    // We already have renovate for this
                    checkDependencies = false
                    disable += listOf(
                        "GradleDependency",
                        "AndroidGradlePluginVersion",
                    )
                    // Since we use baseline we should not have full path in the files
                    absolutePaths = false

                    // Update some rules issue level
                    error += "LogNotTimber"
                }

                dependencies {
                    "lintChecks"(project(":lint"))

                    "coreLibraryDesugaring"(libs.tools.desugar.jdk)

                    "implementation"(libs.timber)
                    "implementation"(libs.kotlinx.serialization.json)

                    "ksp"(libs.hilt.android.compiler)
                    "implementation"(libs.hilt.android)

                    "testRuntimeOnly"(libs.junit.platform.launcher)

                    "testImplementation"(platform(libs.junit.bom))
                    "testImplementation"(libs.kotlinx.coroutines.test)
                    "testImplementation"(libs.junit.jupiter)
                    "testImplementation"(libs.junit.vintage.engine)
                    "testImplementation"(libs.mockk)
                    "testImplementation"(libs.robolectric)
                    "testImplementation"(libs.turbine)

                    "testImplementation"(project(":testing-unit"))
                }
            }

            File(testResourcesDir, "robolectric.properties").apply {
                val sdkVersion = libs.versions.robolectric.target.sdk.get().toInt()
                writeText("sdk=$sdkVersion")
            }
        }
    }
}
