package io.homeassistant.companion.android.common.data.integration

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.format.DateUtils
import android.util.Log
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryOptions
import io.homeassistant.companion.android.common.util.HaIconTypeface
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Locale
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
    const val ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY = 2

    val DOMAINS_PRESS = listOf("button", "input_button")
    val DOMAINS_TOGGLE = listOf(
        "automation", "cover", "fan", "humidifier", "input_boolean", "light", "lock",
        "media_player", "remote", "siren", "switch"
    )

    val APP_PRESS_ACTION_DOMAINS = DOMAINS_PRESS + DOMAINS_TOGGLE + listOf(
        "scene",
        "script"
    )

    val STATE_COLORED_DOMAINS = listOf(
        "alarm_control_panel",
        "alert",
        "automation",
        "binary_sensor",
        "calendar",
        "camera",
        "climate",
        "cover",
        "device_tracker",
        "fan",
        "group",
        "humidifier",
        "input_boolean",
        "lawn_mower",
        "light",
        "lock",
        "media_player",
        "person",
        "plant",
        "remote",
        "schedule",
        "script",
        "siren",
        "sun",
        "switch",
        "timer",
        "update",
        "vacuum",
        "water_heater"
    )
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
            newLastUpdated =
                Calendar.getInstance().apply { timeInMillis = round(it * 1000).toLong() }
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
        ) {
            return null
        }

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

fun <T> Entity<T>.supportsAlarmControlPanelArmAway(): Boolean {
    return try {
        if (domain != "alarm_control_panel") return false
        ((attributes as Map<*, *>)["supported_features"] as Int) and EntityExt.ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY == EntityExt.ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get supportsArmedAway", e)
        false
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

        return calculateNumStep(
            ((attributes as Map<*, *>)["percentage_step"] as? Double)?.toDouble() ?: 1.0
        ) - 1
    } catch (e: Exception) {
        Log.e(EntityExt.TAG, "Unable to get getFanSteps", e)
        null
    }
}

fun <T> Entity<T>.supportsLightBrightness(): Boolean {
    return try {
        if (domain != "light") return false

        // On HA Core 2021.5 and later brightness detection has changed
        // to simplify things in the app lets use both methods for now
        val supportedColorModes =
            (attributes as Map<*, *>)["supported_color_modes"] as? List<String>
        val supportsBrightness =
            if (supportedColorModes == null) false else (supportedColorModes - EntityExt.LIGHT_MODE_NO_BRIGHTNESS_SUPPORT.toSet()).isNotEmpty()
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
                    ((attributes as Map<*, *>)["brightness"] as? Number)?.toFloat()?.div(255f)
                        ?.times(100)
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

        val supportedColorModes =
            (attributes as Map<*, *>)["supported_color_modes"] as? List<String>
        val supportsColorTemp =
            supportedColorModes?.contains(EntityExt.LIGHT_MODE_COLOR_TEMP) ?: false
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

fun <T> Entity<T>.getIcon(context: Context): IIcon {
    val attributes = this.attributes as Map<String, Any?>
    val icon = attributes["icon"] as? String
    return if (icon?.startsWith("mdi") == true) {
        val mdiIcon = icon.split(":")[1]
        if (mdiIcon == "home-assistant") {
            return HaIconTypeface.Icon.mdi_home_assistant
        }
        return IconicsDrawable(context, "cmd-$mdiIcon").icon ?: CommunityMaterial.Icon.cmd_bookmark
    } else {
        /**
         * Return a default icon for the domain that matches the icon used in the frontend, see
         * icons.json in the component's core integration.
         * Note: for SimplifiedEntity sometimes return a more general icon because we don't have state.
         */
        val compareState =
            state.ifBlank { attributes["state"] as String? }
        when (domain) {
            "air_quality" -> CommunityMaterial.Icon.cmd_air_filter
            "alarm_control_panel" -> when (compareState) {
                "armed_away" -> CommunityMaterial.Icon3.cmd_shield_lock
                "armed_custom_bypass" -> CommunityMaterial.Icon3.cmd_security
                "armed_home" -> CommunityMaterial.Icon3.cmd_shield_home
                "armed_night" -> CommunityMaterial.Icon3.cmd_shield_moon
                "armed_vacation" -> CommunityMaterial.Icon3.cmd_shield_airplane
                "disarmed" -> CommunityMaterial.Icon3.cmd_shield_off
                "pending" -> CommunityMaterial.Icon3.cmd_shield_outline
                "triggered" -> CommunityMaterial.Icon.cmd_bell_ring
                else -> CommunityMaterial.Icon3.cmd_shield
            }
            "alert" -> CommunityMaterial.Icon.cmd_alert
            "automation" -> if (compareState == "off") {
                CommunityMaterial.Icon3.cmd_robot_off
            } else {
                CommunityMaterial.Icon3.cmd_robot
            }
            "binary_sensor" -> binarySensorIcon(compareState, this as Entity<Map<String, Any?>>)
            "button" -> when (attributes["device_class"]) {
                "restart" -> CommunityMaterial.Icon3.cmd_restart
                "update" -> CommunityMaterial.Icon3.cmd_package_up
                else -> CommunityMaterial.Icon2.cmd_gesture_tap_button
            }
            "calendar" -> CommunityMaterial.Icon.cmd_calendar
            "camera" -> if (compareState == "off") {
                CommunityMaterial.Icon3.cmd_video_off
            } else {
                CommunityMaterial.Icon3.cmd_video
            }
            "climate" -> CommunityMaterial.Icon3.cmd_thermostat
            "configurator" -> CommunityMaterial.Icon.cmd_cog
            "conversation" -> CommunityMaterial.Icon3.cmd_microphone_message
            "cover" -> coverIcon(compareState, this as Entity<Map<String, Any?>>)
            "counter" -> CommunityMaterial.Icon.cmd_counter
            "fan" -> if (compareState == "off") {
                CommunityMaterial.Icon2.cmd_fan_off
            } else {
                CommunityMaterial.Icon2.cmd_fan
            }
            "google_assistant" -> CommunityMaterial.Icon2.cmd_google_assistant
            "group" -> CommunityMaterial.Icon2.cmd_google_circles_communities
            "homeassistant" -> HaIconTypeface.Icon.mdi_home_assistant
            "homekit" -> CommunityMaterial.Icon2.cmd_home_automation
            "humidifier" -> if (compareState == "off") {
                CommunityMaterial.Icon.cmd_air_humidifier_off
            } else {
                CommunityMaterial.Icon.cmd_air_humidifier
            }
            "image_processing" -> CommunityMaterial.Icon2.cmd_image_filter_frames
            "input_boolean" -> if (!entityId.endsWith(".ha_android_placeholder")) {
                if (compareState == "on") {
                    CommunityMaterial.Icon.cmd_check_circle_outline
                } else {
                    CommunityMaterial.Icon.cmd_close_circle_outline
                }
            } else { // For SimplifiedEntity without state, use a more generic icon
                CommunityMaterial.Icon3.cmd_toggle_switch_outline
            }
            "input_button" -> CommunityMaterial.Icon2.cmd_gesture_tap_button
            "input_datetime" -> if (attributes["has_date"] == false) {
                CommunityMaterial.Icon.cmd_clock
            } else if (attributes["has_time"] == false) {
                CommunityMaterial.Icon.cmd_calendar
            } else {
                CommunityMaterial.Icon.cmd_calendar_clock
            }
            "input_number" -> CommunityMaterial.Icon3.cmd_ray_vertex
            "input_select" -> CommunityMaterial.Icon2.cmd_format_list_bulleted
            "input_text" -> CommunityMaterial.Icon2.cmd_form_textbox
            "lawn_mower" -> CommunityMaterial.Icon3.cmd_robot_mower
            "light" -> CommunityMaterial.Icon2.cmd_lightbulb
            "lock" -> when (compareState) {
                "unlocked", "open" -> CommunityMaterial.Icon2.cmd_lock_open_variant
                "jammed" -> CommunityMaterial.Icon2.cmd_lock_alert
                "locking", "unlocking", "opening" -> CommunityMaterial.Icon2.cmd_lock_clock
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
            "notify" -> CommunityMaterial.Icon3.cmd_message
            "number" -> CommunityMaterial.Icon3.cmd_ray_vertex
            "persistent_notification" -> CommunityMaterial.Icon.cmd_bell
            "person" -> if (compareState == "not_home") {
                CommunityMaterial.Icon.cmd_account_arrow_right
            } else {
                CommunityMaterial.Icon.cmd_account
            }
            "plant" -> CommunityMaterial.Icon2.cmd_flower
            "proximity" -> CommunityMaterial.Icon.cmd_apple_safari
            "remote" -> CommunityMaterial.Icon3.cmd_remote
            "scene" -> CommunityMaterial.Icon3.cmd_palette_outline // Different from frontend: outline version
            "schedule" -> CommunityMaterial.Icon.cmd_calendar_clock
            "script" -> CommunityMaterial.Icon3.cmd_script_text_outline // Different from frontend: outline version
            "select" -> CommunityMaterial.Icon2.cmd_format_list_bulleted
            "sensor" -> sensorIcon(compareState, this as Entity<Map<String, Any?>>)
            "siren" -> CommunityMaterial.Icon.cmd_bullhorn
            "simple_alarm" -> CommunityMaterial.Icon.cmd_bell
            "sun" -> if (compareState == "above_horizon") {
                CommunityMaterial.Icon3.cmd_white_balance_sunny
            } else {
                CommunityMaterial.Icon3.cmd_weather_night
            }
            "switch" -> if (!entityId.endsWith(".ha_android_placeholder")) {
                when (attributes["device_class"]) {
                    "outlet" -> if (compareState == "on") CommunityMaterial.Icon3.cmd_power_plug else CommunityMaterial.Icon3.cmd_power_plug_off
                    "switch" -> if (compareState == "on") CommunityMaterial.Icon3.cmd_toggle_switch_variant else CommunityMaterial.Icon3.cmd_toggle_switch_variant_off
                    else -> CommunityMaterial.Icon2.cmd_flash
                }
            } else { // For SimplifiedEntity without state, use a more generic icon
                CommunityMaterial.Icon2.cmd_light_switch
            }
            "tag" -> CommunityMaterial.Icon3.cmd_tag_outline
            "text" -> CommunityMaterial.Icon2.cmd_form_textbox
            "timer" -> CommunityMaterial.Icon3.cmd_timer_outline
            "update" -> CommunityMaterial.Icon3.cmd_package
            "updater" -> CommunityMaterial.Icon.cmd_cloud_upload
            "vacuum" -> CommunityMaterial.Icon3.cmd_robot_vacuum
            "water_heater" -> if (compareState == "off") {
                CommunityMaterial.Icon3.cmd_water_boiler_off
            } else {
                CommunityMaterial.Icon3.cmd_water_boiler
            }
            "weather" -> when (state) {
                "clear-night" -> CommunityMaterial.Icon3.cmd_weather_night
                "exceptional" -> CommunityMaterial.Icon.cmd_alert_circle_outline
                "fog" -> CommunityMaterial.Icon3.cmd_weather_fog
                "hail" -> CommunityMaterial.Icon3.cmd_weather_hail
                "lightning" -> CommunityMaterial.Icon3.cmd_weather_lightning
                "lightning-rainy" -> CommunityMaterial.Icon3.cmd_weather_lightning_rainy
                "partlycloudy" -> CommunityMaterial.Icon3.cmd_weather_partly_cloudy
                "pouring" -> CommunityMaterial.Icon3.cmd_weather_pouring
                "rainy" -> CommunityMaterial.Icon3.cmd_weather_rainy
                "snowy" -> CommunityMaterial.Icon3.cmd_weather_snowy
                "snowy-rainy" -> CommunityMaterial.Icon3.cmd_weather_snowy_rainy
                "sunny" -> CommunityMaterial.Icon3.cmd_weather_sunny
                "windy" -> CommunityMaterial.Icon3.cmd_weather_windy
                "windy-variant" -> CommunityMaterial.Icon3.cmd_weather_windy_variant
                else -> CommunityMaterial.Icon3.cmd_weather_cloudy
            }
            "zone" -> CommunityMaterial.Icon3.cmd_map_marker_radius
            else -> CommunityMaterial.Icon.cmd_bookmark
        }
    }
}

private fun binarySensorIcon(state: String?, entity: Entity<Map<String, Any?>>): IIcon {
    val isOff = state == "off"

    return when (entity.attributes["device_class"]) {
        "battery" -> if (isOff) CommunityMaterial.Icon.cmd_battery else CommunityMaterial.Icon.cmd_battery_outline
        "battery_charging" -> if (isOff) CommunityMaterial.Icon.cmd_battery else CommunityMaterial.Icon.cmd_battery_charging
        "carbon_monoxide" -> if (isOff) CommunityMaterial.Icon3.cmd_smoke_detector else CommunityMaterial.Icon3.cmd_smoke_detector_alert
        "cold" -> if (isOff) CommunityMaterial.Icon3.cmd_thermometer else CommunityMaterial.Icon3.cmd_snowflake
        "connectivity" -> if (isOff) CommunityMaterial.Icon.cmd_close_network_outline else CommunityMaterial.Icon.cmd_check_network_outline
        "door" -> if (isOff) CommunityMaterial.Icon.cmd_door_closed else CommunityMaterial.Icon.cmd_door_open
        "garage_door" -> if (isOff) CommunityMaterial.Icon2.cmd_garage else CommunityMaterial.Icon2.cmd_garage_open
        "gas", "problem", "safety", "tamper" -> if (isOff) CommunityMaterial.Icon.cmd_check_circle else CommunityMaterial.Icon.cmd_alert_circle
        "heat" -> if (isOff) CommunityMaterial.Icon3.cmd_thermometer else CommunityMaterial.Icon2.cmd_fire
        "light" -> if (isOff) CommunityMaterial.Icon.cmd_brightness_5 else CommunityMaterial.Icon.cmd_brightness_7
        "lock" -> if (isOff) CommunityMaterial.Icon2.cmd_lock else CommunityMaterial.Icon2.cmd_lock_open
        "moisture" -> if (isOff) CommunityMaterial.Icon3.cmd_water_off else CommunityMaterial.Icon3.cmd_water
        "motion" -> if (isOff) CommunityMaterial.Icon3.cmd_motion_sensor_off else CommunityMaterial.Icon3.cmd_motion_sensor
        "occupancy", "presence" -> if (isOff) CommunityMaterial.Icon2.cmd_home_outline else CommunityMaterial.Icon2.cmd_home
        "opening" -> if (isOff) CommunityMaterial.Icon3.cmd_square else CommunityMaterial.Icon3.cmd_square_outline
        "plug", "power" -> if (isOff) CommunityMaterial.Icon3.cmd_power_plug_off else CommunityMaterial.Icon3.cmd_power_plug
        "running" -> if (isOff) CommunityMaterial.Icon3.cmd_stop else CommunityMaterial.Icon3.cmd_play
        "smoke" -> if (isOff) CommunityMaterial.Icon3.cmd_smoke_detector_variant else CommunityMaterial.Icon3.cmd_smoke_detector_variant_alert
        "sound" -> if (isOff) CommunityMaterial.Icon3.cmd_music_note_off else CommunityMaterial.Icon3.cmd_music_note
        "update" -> if (isOff) CommunityMaterial.Icon3.cmd_package else CommunityMaterial.Icon3.cmd_package_up
        "vibration" -> if (isOff) CommunityMaterial.Icon.cmd_crop_portrait else CommunityMaterial.Icon3.cmd_vibrate
        "window" -> if (isOff) CommunityMaterial.Icon3.cmd_window_closed else CommunityMaterial.Icon3.cmd_window_open
        else -> if (isOff) CommunityMaterial.Icon3.cmd_radiobox_blank else CommunityMaterial.Icon.cmd_checkbox_marked_circle
    }
}

private fun coverIcon(state: String?, entity: Entity<Map<String, Any?>>): IIcon {
    val open = state !== "closed"

    return when (entity.attributes["device_class"]) {
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

private fun sensorIcon(state: String?, entity: Entity<Map<String, Any?>>): IIcon {
    var icon: IIcon? = null

    if (entity.attributes["device_class"] != null) {
        icon = when (entity.attributes["device_class"]) {
            "apparent_power", "power", "reactive_power" -> CommunityMaterial.Icon2.cmd_flash
            "aqi" -> CommunityMaterial.Icon.cmd_air_filter
            "atmospheric_pressure" -> CommunityMaterial.Icon3.cmd_thermometer_lines
            "battery" -> {
                val batteryValue = state?.toDoubleOrNull()
                if (batteryValue == null) {
                    when (state) {
                        "off" -> CommunityMaterial.Icon.cmd_battery
                        "on" -> CommunityMaterial.Icon.cmd_battery_alert
                        else -> CommunityMaterial.Icon.cmd_battery_unknown
                    }
                } else if (batteryValue <= 5) {
                    CommunityMaterial.Icon.cmd_battery_alert_variant_outline
                } else {
                    when (((batteryValue / 10) * 10).toInt()) {
                        10 -> CommunityMaterial.Icon.cmd_battery_10
                        20 -> CommunityMaterial.Icon.cmd_battery_20
                        30 -> CommunityMaterial.Icon.cmd_battery_30
                        40 -> CommunityMaterial.Icon.cmd_battery_40
                        50 -> CommunityMaterial.Icon.cmd_battery_50
                        60 -> CommunityMaterial.Icon.cmd_battery_60
                        70 -> CommunityMaterial.Icon.cmd_battery_70
                        80 -> CommunityMaterial.Icon.cmd_battery_80
                        90 -> CommunityMaterial.Icon.cmd_battery_90
                        else -> CommunityMaterial.Icon.cmd_battery
                    }
                }
            }
            "carbon_dioxide" -> CommunityMaterial.Icon3.cmd_molecule_co2
            "carbon_monoxide" -> CommunityMaterial.Icon3.cmd_molecule_co
            "current" -> CommunityMaterial.Icon.cmd_current_ac
            "data_rate" -> CommunityMaterial.Icon3.cmd_transmission_tower
            "data_size" -> CommunityMaterial.Icon.cmd_database
            "date" -> CommunityMaterial.Icon.cmd_calendar
            "distance" -> CommunityMaterial.Icon.cmd_arrow_left_right
            "duration" -> CommunityMaterial.Icon3.cmd_progress_clock
            "energy" -> CommunityMaterial.Icon2.cmd_lightning_bolt
            "frequency", "voltage" -> CommunityMaterial.Icon3.cmd_sine_wave
            "gas" -> CommunityMaterial.Icon3.cmd_meter_gas
            "humidity", "moisture" -> CommunityMaterial.Icon3.cmd_water_percent
            "illuminance" -> CommunityMaterial.Icon.cmd_brightness_5
            "irradiance" -> CommunityMaterial.Icon3.cmd_sun_wireless
            "monetary" -> CommunityMaterial.Icon.cmd_cash
            "nitrogen_dioxide",
            "nitrogen_monoxide",
            "nitrous_oxide",
            "ozone",
            "pm1",
            "pm10",
            "pm25",
            "sulphur_dioxide",
            "volatile_organic_compounds" -> CommunityMaterial.Icon3.cmd_molecule
            "power_factor" -> CommunityMaterial.Icon.cmd_angle_acute
            "precipitation" -> CommunityMaterial.Icon3.cmd_weather_rainy
            "precipitation_intensity" -> CommunityMaterial.Icon3.cmd_weather_pouring
            "pressure" -> CommunityMaterial.Icon2.cmd_gauge
            "signal_strength" -> CommunityMaterial.Icon3.cmd_wifi
            "sound_pressure" -> CommunityMaterial.Icon.cmd_ear_hearing
            "speed" -> CommunityMaterial.Icon3.cmd_speedometer
            "temperature" -> CommunityMaterial.Icon3.cmd_thermometer
            "timestamp" -> CommunityMaterial.Icon.cmd_clock
            "volume" -> CommunityMaterial.Icon.cmd_car_coolant_level
            "water" -> CommunityMaterial.Icon3.cmd_water
            "weight" -> CommunityMaterial.Icon3.cmd_weight
            "wind_speed" -> CommunityMaterial.Icon3.cmd_weather_windy
            else -> null
        }
    }

    if (icon == null) {
        val unitOfMeasurement = entity.attributes["unit_of_measurement"]
        if (unitOfMeasurement != null && unitOfMeasurement in listOf("°C", "°F")) {
            icon = CommunityMaterial.Icon3.cmd_thermometer
        }
    }

    return icon ?: CommunityMaterial.Icon.cmd_eye
}

suspend fun <T> Entity<T>.onPressed(
    integrationRepository: IntegrationRepository
) {
    val action = when (domain) {
        "lock" -> {
            if (state == "unlocked") "lock" else "unlock"
        }
        "alarm_control_panel" -> {
            if (state != "disarmed") "alarm_disarm" else "alarm_arm_away"
        }
        in EntityExt.DOMAINS_PRESS -> "press"
        "fan",
        "input_boolean",
        "script",
        "switch" -> {
            if (state == "on") "turn_off" else "turn_on"
        }
        "scene" -> "turn_on"
        else -> "toggle"
    }

    integrationRepository.callAction(
        domain = this.domain,
        action = action,
        actionData = hashMapOf("entity_id" to entityId)
    )
}

/**
 * Execute an app press action like [Entity.onPressed], but without a current state if possible to
 * speed up the execution.
 * @throws IntegrationException on network errors
 */
suspend fun onEntityPressedWithoutState(
    entityId: String,
    integrationRepository: IntegrationRepository
) {
    val domain = entityId.split(".")[0]
    val action = when (domain) {
        "lock" -> {
            val lockEntity = try {
                integrationRepository.getEntity(entityId)
            } catch (e: Exception) {
                null
            }
            if (lockEntity?.state == "locked") "unlock" else "lock"
        }
        in EntityExt.DOMAINS_PRESS -> "press"
        in EntityExt.DOMAINS_TOGGLE -> "toggle"
        else -> "turn_on"
    }
    integrationRepository.callAction(
        domain = domain,
        action = action,
        actionData = hashMapOf("entity_id" to entityId)
    )
}

val <T> Entity<T>.friendlyName: String
    get() = (attributes as? Map<*, *>)?.get("friendly_name")?.toString() ?: entityId

fun <T> Entity<T>.friendlyState(context: Context, options: EntityRegistryOptions? = null, appendUnitOfMeasurement: Boolean = false): String {
    val attributes = this.attributes as Map<String, Any?>

    var friendlyState = when (domain) {
        "binary_sensor" -> {
            // https://github.com/home-assistant/core/blob/dev/homeassistant/components/binary_sensor/strings.json#L113
            when (attributes["device_class"]) {
                "battery" -> if (state == "on") context.getString(commonR.string.state_low) else context.getString(commonR.string.state_normal)
                "battery_charging" -> if (state == "on") context.getString(commonR.string.state_charging) else context.getString(commonR.string.state_not_charging)
                "cold" -> if (state == "on") context.getString(commonR.string.state_cold) else context.getString(commonR.string.state_off)
                "connectivity" -> if (state == "on") context.getString(commonR.string.state_connected) else context.getString(commonR.string.state_disconnected)
                "door", "window", "garage_door", "opening" -> if (state == "on") context.getString(commonR.string.state_open) else context.getString(commonR.string.state_closed)
                "gas" -> if (state == "on") context.getString(commonR.string.state_detected) else context.getString(commonR.string.state_clear)
                "heat" -> if (state == "on") context.getString(commonR.string.state_hot) else context.getString(commonR.string.state_off)
                "light" -> if (state == "on") context.getString(commonR.string.state_light_detected) else context.getString(commonR.string.state_no_light)
                "lock" -> if (state == "on") context.getString(commonR.string.state_unlocked) else context.getString(commonR.string.state_locked)
                "moisture" -> if (state == "on") context.getString(commonR.string.state_wet) else context.getString(commonR.string.state_dry)
                "moving" -> if (state == "on") context.getString(commonR.string.state_moving) else context.getString(commonR.string.state_not_moving)
                "plug" -> if (state == "on") context.getString(commonR.string.state_plugged_in) else context.getString(commonR.string.state_unplugged)
                "presence" -> if (state == "on") context.getString(commonR.string.state_home) else context.getString(commonR.string.state_not_home)
                "problem" -> if (state == "on") context.getString(commonR.string.state_problem) else context.getString(commonR.string.state_ok)
                "running" -> if (state == "on") context.getString(commonR.string.state_running) else context.getString(commonR.string.state_not_running)
                "safety" -> if (state == "on") context.getString(commonR.string.state_unsafe) else context.getString(commonR.string.state_safe)
                "tamper" -> if (state == "on") context.getString(commonR.string.state_tampering_detected) else context.getString(commonR.string.state_off)
                "update" -> if (state == "on") context.getString(commonR.string.state_update_available) else context.getString(commonR.string.state_up_to_date)
                else -> if (state == "on") context.getString(commonR.string.state_on) else context.getString(commonR.string.state_off)
            }
        }
        else -> {
            // https://github.com/home-assistant/frontend/blob/dev/src/common/entity/get_states.ts#L5
            when (state) {
                "above_horizon" -> context.getString(commonR.string.state_above_horizon)
                "active" -> context.getString(commonR.string.state_active)
                "armed_away" -> context.getString(commonR.string.state_armed_away)
                "armed_custom_bypass" -> context.getString(commonR.string.state_armed_custom_bypass)
                "armed_home" -> context.getString(commonR.string.state_armed_home)
                "armed_night" -> context.getString(commonR.string.state_armed_night)
                "armed_vacation" -> context.getString(commonR.string.state_armed_vacation)
                "arming" -> context.getString(commonR.string.state_arming)
                "auto" -> context.getString(commonR.string.state_auto)
                "below_horizon" -> context.getString(commonR.string.state_below_horizon)
                "buffering" -> context.getString(commonR.string.state_buffering)
                "cleaning" -> context.getString(commonR.string.state_cleaning)
                "clear-night" -> context.getString(commonR.string.state_clear_night)
                "cloudy" -> context.getString(commonR.string.state_cloudy)
                "closed" -> context.getString(commonR.string.state_closed)
                "closing" -> context.getString(commonR.string.state_closing)
                "cool" -> context.getString(commonR.string.state_cool)
                "disarmed" -> context.getString(commonR.string.state_disarmed)
                "disarming" -> context.getString(commonR.string.state_disarming)
                "docked" -> context.getString(commonR.string.state_docked)
                "dry" -> context.getString(commonR.string.state_dry)
                "error" -> context.getString(commonR.string.state_error)
                "exceptional" -> context.getString(commonR.string.state_exceptional)
                "fan_only" -> context.getString(commonR.string.state_fan_only)
                "fog" -> context.getString(commonR.string.state_fog)
                "hail" -> context.getString(commonR.string.state_hail)
                "heat" -> context.getString(commonR.string.state_heat)
                "heat_cool" -> context.getString(commonR.string.state_heat_cool)
                "home" -> context.getString(commonR.string.state_home)
                "idle" -> context.getString(commonR.string.state_idle)
                "jammed" -> context.getString(commonR.string.state_jammed)
                "lightning-raining" -> context.getString(commonR.string.state_lightning_raining)
                "lightning" -> context.getString(commonR.string.state_lightning)
                "locked" -> context.getString(commonR.string.state_locked)
                "locking" -> context.getString(commonR.string.state_locking)
                "mowing" -> context.getString(commonR.string.state_mowing)
                "not_home" -> context.getString(commonR.string.state_not_home)
                "off" -> context.getString(commonR.string.state_off)
                "on" -> context.getString(commonR.string.state_on)
                "open" -> context.getString(commonR.string.state_open)
                "opening" -> context.getString(commonR.string.state_opening)
                "partlycloudy" -> context.getString(commonR.string.state_partlycloudy)
                "paused" -> context.getString(commonR.string.state_paused)
                "pending" -> context.getString(commonR.string.state_pending)
                "playing" -> context.getString(commonR.string.state_playing)
                "problem" -> context.getString(commonR.string.state_problem)
                "pouring" -> context.getString(commonR.string.state_pouring)
                "rainy" -> context.getString(commonR.string.state_rainy)
                "recording" -> context.getString(commonR.string.state_recording)
                "returning" -> context.getString(commonR.string.state_returning)
                "snowy-rainy" -> context.getString(commonR.string.state_snowy_rainy)
                "snowy" -> context.getString(commonR.string.state_snowy)
                "standby" -> context.getString(commonR.string.state_standby)
                "streaming" -> context.getString(commonR.string.state_streaming)
                "sunny" -> context.getString(commonR.string.state_sunny)
                "triggered" -> context.getString(commonR.string.state_triggered)
                "unavailable" -> context.getString(commonR.string.state_unavailable)
                "unlocked" -> context.getString(commonR.string.state_unlocked)
                "unlocking" -> context.getString(commonR.string.state_unlocking)
                "unknown" -> context.getString(commonR.string.state_unknown)
                "windy", "windy-variant" -> context.getString(commonR.string.state_windy)
                else -> state
            }
        }
    }
    if (friendlyState == state && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val stateInMillis = ZonedDateTime.parse(state, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant()
                .toEpochMilli()
            friendlyState = DateUtils.getRelativeTimeSpanString(
                stateInMillis,
                System.currentTimeMillis(),
                0,
                DateUtils.FORMAT_ABBREV_ALL
            ).toString()
        } catch (e: DateTimeParseException) { /* Not a timestamp */ }
    }
    if (
        friendlyState == state &&
        canSupportPrecision() &&
        (options?.sensor?.displayPrecision != null || options?.sensor?.suggestedDisplayPrecision != null)
    ) {
        val number = friendlyState.toDouble()
        val precision = options.sensor.displayPrecision ?: options.sensor.suggestedDisplayPrecision!!
        friendlyState = String.format(Locale.getDefault(), "%.${precision}f", number)
    } else if (friendlyState == state) {
        friendlyState = state.split("_").joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
    }

    if (appendUnitOfMeasurement) {
        val unit = (attributes as? Map<*, *>)?.get("unit_of_measurement")?.toString()

        if (unit?.isNotBlank() == true) {
            return "$friendlyState $unit"
        }
    }

    return friendlyState
}

fun <T> Entity<T>.canSupportPrecision() = domain == "sensor" && state.toDoubleOrNull() != null

fun <T> Entity<T>.isExecuting() = when (state) {
    "arming" -> true
    "buffering" -> true
    "closing" -> true
    "disarming" -> true
    "locking" -> true
    "opening" -> true
    "pending" -> true
    "unlocking" -> true
    else -> false
}

fun <T> Entity<T>.isActive() = when {
    // https://github.com/home-assistant/frontend/blob/dev/src/common/entity/state_active.ts
    (domain in listOf("button", "input_button", "event", "scene")) -> state != "unavailable"
    (state == "unavailable" || state == "unknown") -> false
    (state == "off" && domain != "alert") -> false
    (domain == "alarm_control_panel") -> state != "disarmed"
    (domain == "alert") -> state != "idle"
    (domain == "cover") -> state != "closed"
    (domain in listOf("device_tracker", "person")) -> state != "not_home"
    (domain == "lawn_mower") -> state in listOf("mowing", "error")
    // on Android, contrary to HA Frontend, a lock is considered active when locked
    (domain == "lock") -> state == "locked"
    (domain == "media_player") -> state != "standby"
    (domain == "vacuum") -> state !in listOf("idle", "docked", "paused")
    (domain == "plant") -> state == "problem"
    (domain == "group") -> state in listOf("on", "home", "open", "locked", "problem")
    (domain == "timer") -> state == "active"
    (domain == "camera") -> state == "streaming"
    else -> true
}
