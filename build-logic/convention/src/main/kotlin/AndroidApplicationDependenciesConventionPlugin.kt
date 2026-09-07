import com.android.build.api.dsl.ApplicationExtension
import io.homeassistant.companion.android.getPluginId
import io.sentry.android.gradle.extensions.SentryPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project

/**
 * This convention plugin has been created to avoid duplicating dependencies
 * in `:app` and `:automotive` modules.
 *
 * This plugin requires the following:
 * - The Android Application Gradle plugin must be applied to the project.
 * - The project must define at least two product flavors: `full` and `minimal`.
 *   These flavors can be automatically configured by applying the
 *   [AndroidFullMinimalFlavorConventionPlugin].
 */
class AndroidApplicationDependenciesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.android.application.getPluginId())

            configureSentryMappingUpload()

            extensions.getByType<ApplicationExtension>().apply {
                dependencies {
                    "implementation"(project(":common"))
                    "implementation"(project(":microwakeword"))

                    "implementation"(libs.blurView)
                    "implementation"(libs.haze)
                    "implementation"(libs.haze.materials)
                    "implementation"(libs.androidx.health.connect.client)

                    "implementation"(libs.kotlin.stdlib)
                    "implementation"(libs.kotlin.reflect)
                    "implementation"(libs.kotlinx.coroutines.core)
                    "implementation"(libs.kotlinx.coroutines.android)
                    "implementation"(libs.androidx.concurrent.ktx)
                    "fullImplementation"(libs.kotlinx.coroutines.play.services)

                    "implementation"(libs.apache.commons.text)

                    "implementation"(libs.appcompat)
                    "implementation"(libs.androidx.lifecycle.runtime.ktx)
                    "implementation"(libs.androidx.lifecycle.service)
                    "implementation"(libs.constraintlayout)
                    "implementation"(libs.recyclerview)
                    "implementation"(libs.preference.ktx)
                    "implementation"(libs.material)
                    "implementation"(libs.fragment.ktx)

                    "implementation"(platform(libs.okhttp.bom))
                    "implementation"(libs.okhttp.android)

                    "implementation"(libs.bundles.coil)

                    "fullImplementation"(libs.play.services.location)
                    "fullImplementation"(libs.play.services.home)
                    "fullImplementation"(libs.play.services.threadnetwork)
                    "fullImplementation"(platform(libs.firebase.bom))
                    "fullImplementation"(libs.firebase.messaging)
                    "fullImplementation"(libs.sentry.android.core)
                    "fullImplementation"(libs.play.services.wearable)
                    "fullImplementation"(libs.wear.remote.interactions)

                    "implementation"(libs.biometric)
                    "implementation"(libs.webkit)

                    "implementation"(libs.bundles.media3)
                    "fullImplementation"(libs.media3.datasource.cronet)
                    "minimalImplementation"(libs.media3.datasource.cronet) {
                        exclude(group = "com.google.android.gms", module = "play-services-cronet")
                    }
                    "minimalImplementation"(libs.cronet.embedded)

                    "implementation"(libs.compose.animation)
                    "implementation"(libs.compose.material)
                    "implementation"(libs.compose.material.icons.core)
                    "implementation"(libs.compose.material.icons.extended)
                    "implementation"(libs.compose.runtime)
                    "implementation"(libs.activity.compose)
                    "implementation"(libs.navigation.compose)
                    "implementation"(libs.core.remoteviews)
                    "implementation"(libs.core.splashscreen)
                    "implementation"(libs.core.ktx)
                    "implementation"(libs.accompanist.permissions)
                    "implementation"(libs.androidx.hilt.navigation.compose)

                    "implementation"(libs.bundles.androidx.glance)

                    "implementation"(libs.iconics.core)
                    "implementation"(libs.iconics.compose)
                    "implementation"(libs.community.material.typeface)

                    "implementation"(libs.bundles.paging)

                    "implementation"(libs.reorderable)
                    "implementation"(libs.aboutlibraries.compose.m3)

                    "implementation"(libs.zxing)
                    "implementation"(libs.improv)

                    "implementation"(libs.car.core)

                    "androidTestImplementation"(libs.bundles.androidx.test)
                    "androidTestImplementation"(libs.leakcanary.android.instrumentation)
                    "androidTestImplementation"(libs.hilt.android.testing)

                    "testImplementation"(libs.bundles.androidx.glance.testing)
                    "testImplementation"(libs.navigation.test)
                    "testImplementation"(libs.hilt.android.testing)
                    "testImplementation"(libs.androidx.work.testing)

                    "lintChecks"(libs.compose.lint.checks)
                }
            }
        }
    }

    /**
     * Uploads the R8 mapping of the `fullRelease` variant to Sentry so events get exact line
     * numbers and inlined frames. Only the `full` flavor ships the Sentry SDK, so other variants
     * have nothing to upload. The upload only runs when `SENTRY_AUTH_TOKEN` is set (CI release
     * builds); without it the build still injects the ProGuard UUID for a later manual upload.
     * Everything else the plugin can do (auto-instrumentation, dependency injection, telemetry)
     * is disabled.
     */
    private fun Project.configureSentryMappingUpload() {
        apply(plugin = libs.plugins.sentry.getPluginId())

        extensions.configure<SentryPluginExtension> {
            ignoredFlavors.set(setOf("minimal"))
            ignoredBuildTypes.set(setOf("debug"))
            autoUploadProguardMapping.set(System.getenv("SENTRY_AUTH_TOKEN") != null)
            telemetry.set(false)
            autoInstallation.enabled.set(false)
            tracingInstrumentation.enabled.set(false)
        }
    }
}
