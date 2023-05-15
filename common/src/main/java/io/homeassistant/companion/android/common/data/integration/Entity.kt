package io.homeassistant.companion.android.common.data.integration

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.format.DateUtils
import android.util.Log
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Locale
import kotlin.math.round
import io.homeassistant.companion.android.common.R as commonR

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
        Log.e(EntityExt.TAG, "Unable to get getFanSteps")
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
            "homeassistant" -> CommunityMaterial.Icon2.cmd_home_assistant
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
    val service = when (domain) {
        "lock" -> {
            if (state == "unlocked") "lock" else "unlock"
        }
        "cover" -> {
            if (state == "open") "close_cover" else "open_cover"
        }
        "button",
        "input_button" -> "press"
        "fan",
        "input_boolean",
        "script",
        "switch" -> {
            if (state == "on") "turn_off" else "turn_on"
        }
        "scene" -> "turn_on"
        else -> "toggle"
    }

    integrationRepository.callService(
        domain = this.domain,
        service = service,
        serviceData = hashMapOf("entity_id" to entityId)
    )
}

val <T> Entity<T>.friendlyName: String
    get() = (attributes as? Map<*, *>)?.get("friendly_name")?.toString() ?: entityId

fun <T> Entity<T>.friendlyState(context: Context): String {
    var friendlyState = when (state) {
        "closed" -> context.getString(commonR.string.state_closed)
        "closing" -> context.getString(commonR.string.state_closing)
        "jammed" -> context.getString(commonR.string.state_jammed)
        "locked" -> context.getString(commonR.string.state_locked)
        "locking" -> context.getString(commonR.string.state_locking)
        "off" -> context.getString(commonR.string.state_off)
        "on" -> context.getString(commonR.string.state_on)
        "open" -> context.getString(commonR.string.state_open)
        "opening" -> context.getString(commonR.string.state_opening)
        "unavailable" -> context.getString(commonR.string.state_unavailable)
        "unlocked" -> context.getString(commonR.string.state_unlocked)
        "unlocking" -> context.getString(commonR.string.state_unlocking)
        "unknown" -> context.getString(commonR.string.state_unknown)
        else -> state
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
    if (friendlyState == state) {
        friendlyState = state.split("_").joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
    }
    return friendlyState
}

fun <T> Entity<T>.isExecuting() = when (state) {
    "closing" -> true
    "locking" -> true
    "opening" -> true
    "unlocking" -> true
    "buffering" -> true
    "disarming" -> true
    else -> false
}
