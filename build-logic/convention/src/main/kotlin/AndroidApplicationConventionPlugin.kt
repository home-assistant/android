import com.android.build.api.dsl.ApplicationExtension
import io.homeassistant.companion.android.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private const val APPLICATION_ID = "io.homeassistant.companion.android"

/**
 * A convention plugin that applies common configurations to Android application modules.
 * This centralizes configuration, preventing duplication across multiple modules.
 *
 * This plugin applies several Gradle plugins that are commonly used in all application modules.
 *
 * After applying this plugin, the configured values can be overridden if necessary. However,
 * if extensive overrides are required, it may indicate that the configuration should be moved
 * out of this convention plugin.
 *
 * The application's `versionCode` can be set via the `VERSION_CODE` environment variable.
 *
 * A `release` signing configuration is automatically created. The keystore information can be
 * provided through the following environment variables:
 * - `KEYSTORE_PATH`: The path to the keystore file.
 * - `KEYSTORE_PASSWORD`: The password for the keystore.
 * - `KEYSTORE_ALIAS`: The alias for the key within the keystore.
 * - `KEYSTORE_ALIAS_PASSWORD`: The password for the key alias.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.android.application.getPluginId())
            apply(plugin = libs.plugins.kotlin.android.getPluginId())
            apply(plugin = libs.plugins.ksp.getPluginId())
            apply(plugin = libs.plugins.hilt.getPluginId())
            apply(plugin = libs.plugins.compose.compiler.getPluginId())

            extensions.configure<ApplicationExtension> {
                namespace = APPLICATION_ID
                compileSdk = libs.versions.androidSdk.compile.get().toInt()

                defaultConfig {
                    applicationId = APPLICATION_ID
                    minSdk = libs.versions.androidSdk.min.get().toInt()
                    targetSdk = libs.versions.androidSdk.target.get().toInt()

                    versionName = project.version.toString()
                    versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
                }

                buildFeatures {
                    viewBinding = true
                    compose = true
                    buildConfig = true
                }

                compileOptions {
                    isCoreLibraryDesugaringEnabled = true
                    sourceCompatibility(libs.versions.javaVersion.get())
                    targetCompatibility(libs.versions.javaVersion.get())
                }

                tasks.withType<KotlinCompile>().configureEach {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaVersion.get()))
                    }
                }

                signingConfigs {
                    create("release") {
                        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release_keystore.keystore")
                        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                        keyAlias = System.getenv("KEYSTORE_ALIAS") ?: ""
                        keyPassword = System.getenv("KEYSTORE_ALIAS_PASSWORD") ?: ""
                        enableV1Signing = true
                        enableV2Signing = true
                    }
                }

                buildTypes {
                    named("debug").configure {
                        applicationIdSuffix = ".debug"
                    }
                    named("release").configure {
                        isDebuggable = false
                        isJniDebuggable = false
                        signingConfig = signingConfigs.getByName("release")
                    }
                }

                testOptions {
                    unitTests.isReturnDefaultValues = true
                }

                lint {
                    abortOnError = false
                    disable += "MissingTranslation"
                }
            }
        }
    }
}
