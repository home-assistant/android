package io.homeassistant.companion.android.common.data.integration

import android.util.Log
import java.util.Calendar

data class Entity<T>(
    val entityId: String,
    val state: String,
    val attributes: T,
    val lastChanged: Calendar,
    val lastUpdated: Calendar,
    val context: Map<String, Any>?
)

object EntityExt {
    const val TAG = "EntityExt"

    const val LIGHT_MODE_COLOR_TEMP = "color_temp"
    val LIGHT_MODE_NO_BRIGHTNESS_SUPPORT = listOf("unknown", "onoff")
    const val LIGHT_SUPPORT_BRIGHTNESS_DEPR = 1
    const val LIGHT_SUPPORT_COLOR_TEMP_DEPR = 2
}

val <T> Entity<T>.domain: String
    get() = this.entityId.split(".")[0]

fun <T> Entity<T>.supportsLightBrightness(): Boolean {
    return try {
        if (domain != "light") return false

        // On HA Core 2021.5 and later brightness detection has changed
        // to simplify things in the app lets use both methods for now
        val supportedColorModes = (attributes as Map<*, *>)["supported_color_modes"] as? List<String>
        val supportsBrightness =
            if (supportedColorModes == null) false else (supportedColorModes - EntityExt.LIGHT_MODE_NO_BRIGHTNESS_SUPPORT).isNotEmpty()
        val supportedFeatures = attributes["supported_features"] as Int
        supportsBrightness || (supportedFeatures and EntityExt.LIGHT_SUPPORT_BRIGHTNESS_DEPR == EntityExt.LIGHT_SUPPORT_BRIGHTNESS_DEPR)
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get supportsLightBrightness", e)
        false
    }
}

fun <T> Entity<T>.supportsLightColorTemperature(): Boolean {
    return try {
        if (domain != "light") return false

        val supportedColorModes = (attributes as Map<*, *>)["supported_color_modes"] as? List<String>
        val supportsColorTemp = supportedColorModes?.contains(EntityExt.LIGHT_MODE_COLOR_TEMP) ?: false
        val supportedFeatures = attributes["supported_features"] as Int
        supportsColorTemp || (supportedFeatures and EntityExt.LIGHT_SUPPORT_COLOR_TEMP_DEPR == EntityExt.LIGHT_SUPPORT_COLOR_TEMP_DEPR)
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get supportsLightColorTemperature", e)
        false
    }
}
