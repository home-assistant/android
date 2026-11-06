import com.android.build.api.variant.AndroidComponentsExtension
import io.homeassistant.companion.android.androidConfig
import io.homeassistant.companion.android.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

private const val SCREENSHOT_TEST_SUITE = "screenshotTest"
private const val SCREENSHOT_IMAGE_DIFFERENCE_THRESHOLD = 0.00025f // 0.025%
private const val SCREENSHOT_DEFAULT_TARGET_VARIANT = "debug"

// Screenshot test worker memory grows with test count. Increase as needed.
// Tracking: https://issuetracker.google.com/issues/469819154
private const val SCREENSHOT_TEST_MAX_HEAP_SIZE = "3g"

// Most of the worker's footprint is layoutlib's native bitmaps, which sit outside
// maxHeapSize and are only released when the JVM exits. Restarting the worker every
// N test classes caps that growth; without it the app module reaches ~15GB on CI.
private const val SCREENSHOT_TEST_FORK_EVERY = 10L

/**
 * A convention plugin that applies common configurations to Android Compose modules.
 *
 * This plugin applies the Compose compiler plugin and declares the screenshot test suite, which targets
 * the `debug` variant unless another plugin or the module picked its own target variants.
 * It also adds the necessary Compose dependencies and configurations for both runtime and testing.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.compose.compiler.getPluginId())

            androidConfig {
                buildFeatures.compose = true

                testOptions.screenshotTests.create(SCREENSHOT_TEST_SUITE) {
                    engineVersion = libs.versions.screenshot.get()
                    imageDifferenceThreshold = SCREENSHOT_IMAGE_DIFFERENCE_THRESHOLD

                    dependencies {
                        implementation.add(libs.compose.uiTooling)
                        implementation.add(libs.screenshot.validation.api)
                    }
                }

                testOptions.customSuites.named(SCREENSHOT_TEST_SUITE) {
                    configureTestTasks {
                        maxHeapSize = SCREENSHOT_TEST_MAX_HEAP_SIZE
                        forkEvery = SCREENSHOT_TEST_FORK_EVERY
                    }
                }
            }

            extensions.getByType(AndroidComponentsExtension::class.java).finalizeDsl {
                androidConfig {
                    testOptions.screenshotTests.named(SCREENSHOT_TEST_SUITE) {
                        if (targetVariants.isEmpty()) {
                            targetVariants.add(SCREENSHOT_DEFAULT_TARGET_VARIANT)
                        }
                    }
                }
            }

            androidConfig {
                dependencies {
                    "implementation"(platform(libs.compose.bom))
                    "implementation"(libs.compose.foundation)
                    "implementation"(libs.compose.material3)
                    "implementation"(libs.compose.ui)
                    "implementation"(libs.compose.uiTooling)
                    "implementation"(libs.androidx.lifecycle.runtime.compose)

                    "androidTestImplementation"(platform(libs.compose.bom))
                    "androidTestImplementation"(libs.bundles.androidx.compose.ui.test)

                    "testImplementation"(platform(libs.compose.bom))
                    "testImplementation"(libs.bundles.androidx.compose.ui.test)
                }
            }
        }
    }
}
