package io.homeassistant.companion.android.common.data.integration

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import java.util.Calendar
import kotlin.math.round

data class Entity<T>(
    val entityId: String,
    val state: String,
    val attributes: T,
    val lastChanged: Calendar,
    val lastUpdated: Calendar,
    val context: Map<String, Any?>?
)

data class EntityPosition(
    val value: Float,
    val min: Float,
    val max: Float
)

object EntityExt {
    const val TAG = "EntityExt"

    const val FAN_SUPPORT_SET_SPEED = 1
    const val LIGHT_MODE_COLOR_TEMP = "color_temp"
    val LIGHT_MODE_NO_BRIGHTNESS_SUPPORT = listOf("unknown", "onoff")
    const val LIGHT_SUPPORT_BRIGHTNESS_DEPR = 1
    const val LIGHT_SUPPORT_COLOR_TEMP_DEPR = 2
}

val <T> Entity<T>.domain: String
    get() = this.entityId.split(".")[0]

/**
 * Apply a [CompressedStateDiff] to this Entity, and return the [Entity] with updated properties.
 * Based on home-assistant-js-websocket entities `processEvent` function:
 * https://github.com/home-assistant/home-assistant-js-websocket/blob/449fa43668f5316eb31609cd36088c5e82c818e2/lib/entities.ts#L47
 */
fun Entity<Map<String, Any>>.applyCompressedStateDiff(diff: CompressedStateDiff): Entity<Map<String, Any>> {
    var (_, newState, newAttributes, newLastChanged, newLastUpdated, newContext) = this
    diff.plus?.let { plus ->
        plus.state?.let {
            newState = it
        }
        plus.context?.let {
            newContext = if (it is String) {
                newContext?.toMutableMap()?.apply { set("id", it) }
            } else {
                newContext?.plus(it as Map<String, Any?>)
            }
        }
        plus.lastChanged?.let {
            val calendar = Calendar.getInstance().apply { timeInMillis = round(it * 1000).toLong() }
            newLastChanged = calendar
            newLastUpdated = calendar
        } ?: plus.lastUpdated?.let {
            newLastUpdated = Calendar.getInstance().apply { timeInMillis = round(it * 1000).toLong() }
        }
        plus.attributes?.let {
            newAttributes = newAttributes.plus(it)
        }
    }
    diff.minus?.attributes?.let {
        newAttributes = newAttributes.minus(it.toSet())
    }
    return Entity(
        entityId = entityId,
        state = newState,
        attributes = newAttributes,
        lastChanged = newLastChanged,
        lastUpdated = newLastUpdated,
        context = newContext
    )
}

fun <T> Entity<T>.getCoverPosition(): EntityPosition? {
    // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-cover.ts#L33
    return try {
        if (
            domain != "cover" ||
            (attributes as Map<*, *>)["current_position"] == null
        ) return null

        val minValue = 0f
        val maxValue = 100f
        val currentValue = (attributes["current_position"] as? Number)?.toFloat() ?: 0f

        EntityPosition(
            value = currentValue.coerceAtLeast(minValue).coerceAtMost(maxValue),
            min = minValue,
            max = maxValue
        )
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get getCoverPosition", e)
        null
    }
}

fun <T> Entity<T>.supportsFanSetSpeed(): Boolean {
    return try {
        if (domain != "fan") return false
        ((attributes as Map<*, *>)["supported_features"] as Int) and EntityExt.FAN_SUPPORT_SET_SPEED == EntityExt.FAN_SUPPORT_SET_SPEED
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get supportsFanSetSpeed", e)
        false
    }
}

fun <T> Entity<T>.getFanSpeed(): EntityPosition? {
    // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-fan.js#L48
    return try {
        if (!supportsFanSetSpeed()) return null

        val minValue = 0f
        val maxValue = 100f
        val currentValue = ((attributes as Map<*, *>)["percentage"] as? Number)?.toFloat() ?: 0f

        EntityPosition(
            value = currentValue.coerceAtLeast(minValue).coerceAtMost(maxValue),
            min = minValue,
            max = maxValue
        )
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get getFanSpeed", e)
        null
    }
}

fun <T> Entity<T>.getFanSteps(): Int? {
    return try {
        if (!supportsFanSetSpeed()) return null

        fun calculateNumStep(percentageStep: Double): Int {
            val numSteps = round(100 / percentageStep).toInt()
            if (numSteps <= 10) return numSteps
            if (numSteps % 10 == 0) return 10
            return calculateNumStep(percentageStep * 2)
        }

        return calculateNumStep(((attributes as Map<*, *>)["percentage_step"] as? Double)?.toDouble() ?: 1.0) - 1
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get getFanSteps")
        null
    }
}

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

fun <T> Entity<T>.getLightBrightness(): EntityPosition? {
    // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-light.ts#L90
    return try {
        if (!supportsLightBrightness()) return null

        when (state) {
            "on" -> {
                val minValue = 0f
                val maxValue = 100f
                val currentValue =
                    ((attributes as Map<*, *>)["brightness"] as? Number)?.toFloat()?.div(255f)?.times(100)
                        ?: 0f

                EntityPosition(
                    value = currentValue.coerceAtLeast(minValue).coerceAtMost(maxValue),
                    min = minValue,
                    max = maxValue
                )
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get getLightBrightness", e)
        null
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

fun <T> Entity<T>.getLightColor(): Int? {
    // https://github.com/home-assistant/frontend/blob/dev/src/panels/lovelace/cards/hui-light-card.ts#L243
    return try {
        if (domain != "light") return null

        when {
            state != "off" && (attributes as Map<*, *>)["rgb_color"] != null -> {
                val (r, g, b) = (attributes["rgb_color"] as List<Int>)
                Color.rgb(r, g, b)
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get getLightColor", e)
        null
    }
}

fun <T> Entity<T>.getIcon(context: Context): IIcon? {
    val attributes = this.attributes as Map<String, Any?>
    val icon = attributes["icon"] as? String
    return if (icon?.startsWith("mdi") == true) {
        val mdiIcon = icon.split(":")[1]
        return IconicsDrawable(context, "cmd-$mdiIcon").icon ?: CommunityMaterial.Icon.cmd_bookmark
    } else {
        /**
         * Return a default icon for the domain that matches the icon used in the frontend, see
         * https://github.com/home-assistant/frontend/blob/dev/src/common/entity/domain_icon.ts.
         * Note: for SimplifiedEntity sometimes return a more general icon because we don't have state.
         */
        val compareState =
            state.ifBlank { attributes["state"] as String? }
        when (domain) {
            "alert" -> CommunityMaterial.Icon.cmd_alert
            "air_quality" -> CommunityMaterial.Icon.cmd_air_filter
            "automation" -> CommunityMaterial.Icon3.cmd_robot
            "button" -> when (attributes["device_class"]) {
                "restart" -> CommunityMaterial.Icon3.cmd_restart
                "update" -> CommunityMaterial.Icon3.cmd_package_up
                else -> CommunityMaterial.Icon2.cmd_gesture_tap_button
            }
            "calendar" -> CommunityMaterial.Icon.cmd_calendar
            "camera" -> CommunityMaterial.Icon3.cmd_video
            "climate" -> CommunityMaterial.Icon3.cmd_thermostat
            "configurator" -> CommunityMaterial.Icon.cmd_cog
            "conversation" -> CommunityMaterial.Icon3.cmd_microphone_message
            "cover" -> coverIcon(compareState, this as Entity<Map<String, Any?>>)
            "counter" -> CommunityMaterial.Icon.cmd_counter
            "fan" -> CommunityMaterial.Icon2.cmd_fan
            "google_assistant" -> CommunityMaterial.Icon2.cmd_google_assistant
            "group" -> CommunityMaterial.Icon2.cmd_google_circles_communities
            "homeassistant" -> CommunityMaterial.Icon2.cmd_home_assistant
            "homekit" -> CommunityMaterial.Icon2.cmd_home_automation
            "humidifier" -> if (compareState == "off")
                CommunityMaterial.Icon.cmd_air_humidifier_off
            else
                CommunityMaterial.Icon.cmd_air_humidifier
            "image_processing" -> CommunityMaterial.Icon2.cmd_image_filter_frames
            "input_boolean" -> if (!entityId.endsWith(".ha_android_placeholder")) {
                if (compareState == "on")
                    CommunityMaterial.Icon.cmd_check_circle_outline
                else
                    CommunityMaterial.Icon.cmd_close_circle_outline
            } else { // For SimplifiedEntity without state, use a more generic icon
                CommunityMaterial.Icon3.cmd_toggle_switch_outline
            }
            "input_button" -> CommunityMaterial.Icon2.cmd_gesture_tap_button
            "input_datetime" -> if (attributes["has_date"] == false)
                CommunityMaterial.Icon.cmd_clock
            else if (attributes["has_time"] == false)
                CommunityMaterial.Icon.cmd_calendar
            else
                CommunityMaterial.Icon.cmd_calendar_clock
            "input_select" -> CommunityMaterial.Icon2.cmd_form_dropdown
            "input_text" -> CommunityMaterial.Icon2.cmd_form_textbox
            "light" -> CommunityMaterial.Icon2.cmd_lightbulb
            "lock" -> when (compareState) {
                "unlocked" -> CommunityMaterial.Icon2.cmd_lock_open
                "jammed" -> CommunityMaterial.Icon2.cmd_lock_alert
                "locking", "unlocking" -> CommunityMaterial.Icon2.cmd_lock_clock
                else -> CommunityMaterial.Icon2.cmd_lock
            }
            "mailbox" -> CommunityMaterial.Icon3.cmd_mailbox
            "media_player" -> when (attributes["device_class"]) {
                "speaker" -> when (compareState) {
                    "playing" -> CommunityMaterial.Icon3.cmd_speaker_play
                    "paused" -> CommunityMaterial.Icon3.cmd_speaker_pause
                    "off" -> CommunityMaterial.Icon3.cmd_speaker_off
                    else -> CommunityMaterial.Icon3.cmd_speaker
                }
                "tv" -> when (compareState) {
                    "playing" -> CommunityMaterial.Icon3.cmd_television_play
                    "paused" -> CommunityMaterial.Icon3.cmd_television_pause
                    "off" -> CommunityMaterial.Icon3.cmd_television_off
                    else -> CommunityMaterial.Icon3.cmd_television
                }
                "receiver" -> when (compareState) {
                    "off" -> CommunityMaterial.Icon.cmd_audio_video_off
                    else -> CommunityMaterial.Icon.cmd_audio_video
                }
                else -> when (compareState) {
                    "playing", "paused" -> CommunityMaterial.Icon.cmd_cast_connected
                    "off" -> CommunityMaterial.Icon.cmd_cast_off
                    else -> CommunityMaterial.Icon.cmd_cast
                }
            }
            "notify" -> CommunityMaterial.Icon.cmd_comment_alert
            "number" -> CommunityMaterial.Icon3.cmd_ray_vertex
            "persistent_notification" -> CommunityMaterial.Icon.cmd_bell
            "person" -> CommunityMaterial.Icon.cmd_account
            "plant" -> CommunityMaterial.Icon2.cmd_flower
            "proximity" -> CommunityMaterial.Icon.cmd_apple_safari
            "remote" -> CommunityMaterial.Icon3.cmd_remote
            "scene" -> CommunityMaterial.Icon3.cmd_palette_outline // Different from frontend: outline version
            "script" -> CommunityMaterial.Icon3.cmd_script_text_outline // Different from frontend: outline version
            "select" -> CommunityMaterial.Icon2.cmd_format_list_bulleted
            "sensor" -> CommunityMaterial.Icon.cmd_eye
            "siren" -> CommunityMaterial.Icon.cmd_bullhorn
            "simple_alarm" -> CommunityMaterial.Icon.cmd_bell
            "sun" -> if (compareState == "above_horizon")
                CommunityMaterial.Icon3.cmd_white_balance_sunny
            else
                CommunityMaterial.Icon3.cmd_weather_night
            "switch" -> if (!entityId.endsWith(".ha_android_placeholder")) {
                when (attributes["device_class"]) {
                    "outlet" -> if (compareState == "on") CommunityMaterial.Icon3.cmd_power_plug else CommunityMaterial.Icon3.cmd_power_plug_off
                    "switch" -> if (compareState == "on") CommunityMaterial.Icon3.cmd_toggle_switch else CommunityMaterial.Icon3.cmd_toggle_switch_off
                    else -> CommunityMaterial.Icon2.cmd_flash
                }
            } else { // For SimplifiedEntity without state, use a more generic icon
                CommunityMaterial.Icon2.cmd_light_switch
            }
            "timer" -> CommunityMaterial.Icon3.cmd_timer_outline
            "updater" -> CommunityMaterial.Icon.cmd_cloud_upload
            "vacuum" -> CommunityMaterial.Icon3.cmd_robot_vacuum
            "water_heater" -> CommunityMaterial.Icon3.cmd_thermometer
            "weather" -> CommunityMaterial.Icon3.cmd_weather_cloudy
            "zone" -> CommunityMaterial.Icon3.cmd_map_marker_radius
            else -> CommunityMaterial.Icon.cmd_bookmark
        }
    }
}

private fun coverIcon(state: String?, entity: Entity<Map<String, Any?>>): IIcon {
    val open = state !== "closed"

    return when (entity.attributes?.get("device_class")) {
        "garage" -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_up_box
            "closing" -> CommunityMaterial.Icon.cmd_arrow_down_box
            "closed" -> CommunityMaterial.Icon2.cmd_garage
            else -> CommunityMaterial.Icon2.cmd_garage_open
        }
        "gate" -> when (state) {
            "opening", "closing" -> CommunityMaterial.Icon2.cmd_gate_arrow_right
            "closed" -> CommunityMaterial.Icon2.cmd_gate
            else -> CommunityMaterial.Icon2.cmd_gate_open
        }
        "door" -> if (open) CommunityMaterial.Icon.cmd_door_open else CommunityMaterial.Icon.cmd_door_closed
        "damper" -> if (open) CommunityMaterial.Icon.cmd_circle else CommunityMaterial.Icon.cmd_circle_slice_8
        "shutter" -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_up_box
            "closing" -> CommunityMaterial.Icon.cmd_arrow_down_box
            "closed" -> CommunityMaterial.Icon3.cmd_window_shutter
            else -> CommunityMaterial.Icon3.cmd_window_shutter_open
        }
        "curtain" -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_split_vertical
            "closing" -> CommunityMaterial.Icon.cmd_arrow_collapse_horizontal
            "closed" -> CommunityMaterial.Icon.cmd_curtains_closed
            else -> CommunityMaterial.Icon.cmd_curtains
        }
        "blind", "shade" -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_up_box
            "closing" -> CommunityMaterial.Icon.cmd_arrow_down_box
            "closed" -> CommunityMaterial.Icon.cmd_blinds
            else -> CommunityMaterial.Icon.cmd_blinds_open
        }
        else -> when (state) {
            "opening" -> CommunityMaterial.Icon.cmd_arrow_up_box
            "closing" -> CommunityMaterial.Icon.cmd_arrow_down_box
            "closed" -> CommunityMaterial.Icon3.cmd_window_closed
            else -> CommunityMaterial.Icon3.cmd_window_open
        }
    }
}
