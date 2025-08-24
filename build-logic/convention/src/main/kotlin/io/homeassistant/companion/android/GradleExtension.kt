package io.homeassistant.companion.android

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.plugin.use.PluginDependency

internal fun Provider<PluginDependency>.getPluginId(): String {
    return get().pluginId
}

internal fun Project.androidConfig(block: CommonExtension<*, *, *, *, *, *>.() -> Unit) {
    when (extensions.findByName("android")) {
        is LibraryExtension -> extensions.configure<LibraryExtension> {
            block()
        }
        is ApplicationExtension -> extensions.configure<ApplicationExtension> {
            block()
        }
    }
}
