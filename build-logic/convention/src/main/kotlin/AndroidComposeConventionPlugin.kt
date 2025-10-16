
import com.android.compose.screenshot.gradle.ScreenshotTestOptions
import com.android.compose.screenshot.tasks.PreviewScreenshotValidationTask
import io.homeassistant.companion.android.androidConfig
import io.homeassistant.companion.android.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * A convention plugin that applies common configurations to Android Compose modules.
 *
 * This plugin applies the Compose compiler plugin and the screenshot test plugin.
 * It also adds the necessary Compose dependencies and configurations for both runtime and testing.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.compose.compiler.getPluginId())
            apply(plugin = libs.plugins.screenshot.getPluginId())

            androidConfig {
                buildFeatures {
                    compose = true
                }

                experimentalProperties["android.experimental.enableScreenshotTest"] = true

                extensions.configure<ScreenshotTestOptions> {
                    imageDifferenceThreshold = 0.00025f // 0.025%
                }
            }

            tasks.withType<PreviewScreenshotValidationTask>().configureEach {
                // Hack until we get the update of the screenshot libray
                // https://issuetracker.google.com/issues/444048026
                // 3g is the minimal value for our tests to pass currently
                maxHeapSize = "3g"
            }

            androidConfig {
                dependencies {
                    "implementation"(platform(libs.compose.bom))
                    "implementation"(libs.compose.foundation)
                    "implementation"(libs.compose.material3)
                    "implementation"(libs.compose.material.icons.core)
                    "implementation"(libs.compose.ui)
                    "implementation"(libs.compose.uiTooling)
                    "implementation"(libs.androidx.lifecycle.runtime.compose)

                    "androidTestImplementation"(platform(libs.compose.bom))
                    "androidTestImplementation"(libs.bundles.androidx.compose.ui.test)

                    "testImplementation"(platform(libs.compose.bom))
                    "testImplementation"(libs.bundles.androidx.compose.ui.test)

                    "screenshotTestImplementation"(libs.compose.uiTooling)
                    "screenshotTestImplementation"(libs.screenshot.validation.api)
                }
            }
        }
    }
}
