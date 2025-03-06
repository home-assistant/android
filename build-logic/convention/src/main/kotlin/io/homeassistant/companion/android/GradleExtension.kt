package io.homeassistant.companion.android

import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency

internal fun Provider<PluginDependency>.getPluginId(): String {
    return get().pluginId
}
