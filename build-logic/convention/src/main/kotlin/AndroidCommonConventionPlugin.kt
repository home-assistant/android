import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import io.homeassistant.companion.android.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
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
            apply(plugin = libs.plugins.ksp.getPluginId())
            apply(plugin = libs.plugins.hilt.getPluginId())

            fun CommonExtension<*, *, *, *, *, *>.configure() {
                compileSdk = libs.versions.androidSdk.compile.get().toInt()

                defaultConfig {
                    minSdk = libs.versions.androidSdk.min.get().toInt()
                }

                buildFeatures {
                    buildConfig = true
                }

                compileOptions {
                    sourceCompatibility(libs.versions.javaVersion.get())
                    targetCompatibility(libs.versions.javaVersion.get())
                }

                tasks.withType<KotlinCompile>().configureEach {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(libs.versions.javaVersion.get()))
                    }
                }

                testOptions {
                    unitTests.isReturnDefaultValues = true
                }

                tasks.withType<Test> {
                    useJUnitPlatform()
                }

                lint {
                    abortOnError = false
                    disable += "MissingTranslation"
                    sarifReport = true
                }
            }

            when (extensions.findByName("android")) {
                is ApplicationExtension -> extensions.configure<ApplicationExtension> { configure() }
                is LibraryExtension -> extensions.configure<LibraryExtension> { configure() }
            }
        }
    }
}
