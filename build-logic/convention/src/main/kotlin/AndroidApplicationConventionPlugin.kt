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
 * This avoid duplicating the same configuration across multiple modules.
 *
 * It applies multiple gradle plugins that are used in every modules.
 *
 * Once this plugin is applied, the values set can be overridden if needed. But it probably
 * means that it should not in this convention plugin anymore.
 *
 * application `version code` can be set in the environment variable VERSION_CODE
 * keystore information can be set in the environment variables:
 * - KEYSTORE_PATH for the path to the keystore
 * - KEYSTORE_PASSWORD for the store password
 * - KEYSTORE_ALIAS for the alias
 * - KEYSTORE_ALIAS_PASSWORD for the alias password
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.android.application.getPluginId())
            apply(plugin = libs.plugins.kotlin.android.getPluginId())
            apply(plugin = libs.plugins.ksp.getPluginId())
            apply(plugin = libs.plugins.hilt.getPluginId())
            apply(plugin = libs.plugins.kotlin.parcelize.getPluginId())
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

                flavorDimensions.add("version")
                productFlavors {
                    create("minimal") {
                        applicationIdSuffix = ".minimal"
                        versionNameSuffix = "-minimal"
                    }
                    create("full") {
                        applicationIdSuffix = ""
                        versionNameSuffix = "-full"
                    }

                    // Generate a list of application ids into BuildConfig
                    val values = productFlavors.joinToString {
                        "\"${it.applicationId ?: defaultConfig.applicationId}${it.applicationIdSuffix}\""
                    }

                    defaultConfig.buildConfigField("String[]", "APPLICATION_IDS", "{$values}")
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
