import com.android.build.api.dsl.ApplicationExtension
import io.homeassistant.companion.android.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
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

            extensions.getByType<ApplicationExtension>().apply {
                dependencies {
                    "implementation"(project(":common"))

                    "implementation"(libs.blurView)
                    "fullImplementation"(libs.androidx.health.connect.client)

                    "implementation"(libs.kotlin.stdlib)
                    "implementation"(libs.kotlin.reflect)
                    "implementation"(libs.kotlinx.coroutines.core)
                    "implementation"(libs.kotlinx.coroutines.android)
                    "fullImplementation"(libs.kotlinx.coroutines.play.services)

                    "implementation"(libs.appcompat)
                    "implementation"(libs.androidx.lifecycle.runtime.ktx)
                    "implementation"(libs.constraintlayout)
                    "implementation"(libs.recyclerview)
                    "implementation"(libs.preference.ktx)
                    "implementation"(libs.material)
                    "implementation"(libs.fragment.ktx)

                    "implementation"(libs.okhttp)

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

                    "implementation"(platform(libs.compose.bom))
                    "implementation"(libs.compose.animation)
                    "implementation"(libs.compose.foundation)
                    "implementation"(libs.compose.material)
                    "implementation"(libs.compose.material.icons.core)
                    "implementation"(libs.compose.material.icons.extended)
                    "implementation"(libs.compose.runtime)
                    "implementation"(libs.compose.ui)
                    "implementation"(libs.compose.uiTooling)
                    "implementation"(libs.activity.compose)
                    "implementation"(libs.navigation.compose)
                    "implementation"(libs.androidx.lifecycle.runtime.compose)
                    "implementation"(libs.core.remoteviews)

                    "implementation"(libs.bundles.androidx.glance)

                    "implementation"(libs.iconics.core)
                    "implementation"(libs.iconics.compose)
                    "implementation"(libs.community.material.typeface)

                    "implementation"(libs.bundles.paging)

                    "implementation"(libs.reorderable)
                    "implementation"(libs.changeLog)

                    "implementation"(libs.zxing)
                    "implementation"(libs.improv)

                    "implementation"(libs.car.core)

                    "androidTestImplementation"(platform(libs.compose.bom))
                    "androidTestImplementation"(libs.bundles.androidx.test)
                    "androidTestImplementation"(libs.bundles.androidx.compose.ui.test)
                    "androidTestImplementation"(libs.leakcanary.android.instrumentation)

                    "testImplementation"(libs.bundles.androidx.glance.testing)
                }
            }
        }
    }
}
