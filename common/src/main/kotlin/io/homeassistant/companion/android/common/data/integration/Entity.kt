package io.homeassistant.companion.android.common.data.integration

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.format.DateUtils
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon2
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon3
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryOptions
import io.homeassistant.companion.android.common.util.LocalDateTimeSerializer
import io.homeassistant.companion.android.common.util.MapAnySerializer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.round
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import timber.log.Timber

@Serializable
data class Entity(
    val entityId: String,
    val state: String,
    @Serializable(with = MapAnySerializer::class)
    val attributes: Map<String, @Polymorphic Any?>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastChanged: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdated: LocalDateTime,
)

data class EntityPosition(val value: Float, val min: Float, val max: Float)

object EntityExt {
    const val TAG = "EntityExt"

    const val FAN_SUPPORT_SET_SPEED = 1
    const val LIGHT_MODE_COLOR_TEMP = "color_temp"
    val LIGHT_MODE_NO_BRIGHTNESS_SUPPORT = listOf("unknown", "onoff")
    const val LIGHT_SUPPORT_BRIGHTNESS_DEPR = 1
    const val LIGHT_SUPPORT_COLOR_TEMP_DEPR = 2
    const val ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY = 2
    const val MEDIA_PLAYER_SUPPORT_VOLUME_SET = 4

    val DOMAINS_PRESS = listOf("button", "input_button")
    val DOMAINS_TOGGLE = listOf(
        "automation", "cover", "fan", "humidifier", "input_boolean", "light", "lock",
        MEDIA_PLAYER_DOMAIN, "remote", "siren", "switch",
    )

    val APP_PRESS_ACTION_DOMAINS = DOMAINS_PRESS + DOMAINS_TOGGLE + listOf(
        "scene",
        "script",
    )

    val STATE_COLORED_DOMAINS = listOf(
        "alarm_control_panel",
        "alert",
        "automation",
        "binary_sensor",
        "calendar",
        CAMERA_DOMAIN,
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
        MEDIA_PLAYER_DOMAIN,
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
        "water_heater",
    )
}

val Entity.domain: String
    get() = this.entityId.split(".")[0]

/**
 * Apply a [CompressedStateDiff] to this Entity, and return the [Entity] with updated properties.
 * Based on home-assistant-js-websocket entities `processEvent` function:
 * https://github.com/home-assistant/home-assistant-js-websocket/blob/449fa43668f5316eb31609cd36088c5e82c818e2/lib/entities.ts#L47
 */
fun Entity.applyCompressedStateDiff(diff: CompressedStateDiff): Entity {
    var (_, newState, newAttributes, newLastChanged, newLastUpdated) = this
    diff.plus?.let { plus ->
        plus.state?.let {
            newState = it
        }
        plus.lastChanged?.let {
            val dateTime = LocalDateTime.ofEpochSecond(round(it).toLong(), 0, ZoneOffset.UTC)
            newLastChanged = dateTime
            newLastUpdated = dateTime
        } ?: plus.lastUpdated?.let {
            newLastUpdated = LocalDateTime.ofEpochSecond(round(it).toLong(), 0, ZoneOffset.UTC)
        }
        plus.attributes.let {
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
    )
}

fun Entity.getCoverPosition(): EntityPosition? {
    // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-cover.ts#L33
    return try {
        if (
            domain != "cover" ||
            attributes["current_position"] == null
        ) {
            return null
        }

        val minValue = 0f
        val maxValue = 100f
        val currentValue = (attributes["current_position"] as? Number)?.toFloat() ?: 0f

        EntityPosition(
            value = currentValue.coerceAtLeast(minValue).coerceAtMost(maxValue),
            min = minValue,
            max = maxValue,
        )
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get getCoverPosition")
        null
    }
}

fun Entity.supportsAlarmControlPanelArmAway(): Boolean {
    return try {
        if (domain != "alarm_control_panel") return false
        (attributes["supported_features"] as Number).toInt() and
            EntityExt.ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY == EntityExt.ALARM_CONTROL_PANEL_SUPPORT_ARM_AWAY
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get supportsArmedAway")
        false
    }
}

fun Entity.supportsFanSetSpeed(): Boolean {
    return try {
        if (domain != "fan") return false
        (attributes["supported_features"] as Number).toInt() and
            EntityExt.FAN_SUPPORT_SET_SPEED == EntityExt.FAN_SUPPORT_SET_SPEED
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get supportsFanSetSpeed")
        false
    }
}

fun Entity.getFanSpeed(): EntityPosition? {
    // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-fan.js#L48
    return try {
        if (!supportsFanSetSpeed()) return null

        val minValue = 0f
        val maxValue = 100f
        val currentValue = (attributes["percentage"] as? Number)?.toFloat() ?: 0f

        EntityPosition(
            value = currentValue.coerceAtLeast(minValue).coerceAtMost(maxValue),
            min = minValue,
            max = maxValue,
        )
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get getFanSpeed")
        null
    }
}

fun Entity.getFanSteps(): Int? {
    return try {
        if (!supportsFanSetSpeed()) return null

        fun calculateNumStep(percentageStep: Double): Int {
            val numSteps = round(100 / percentageStep).toInt()
            if (numSteps <= 10) return numSteps
            if (numSteps % 10 == 0) return 10
            return calculateNumStep(percentageStep * 2)
        }

        return calculateNumStep(
            (attributes["percentage_step"] as? Number)?.toDouble() ?: 1.0,
        ) - 1
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get getFanSteps")
        null
    }
}

fun Entity.supportsLightBrightness(): Boolean {
    return try {
        if (domain != "light") return false

        // On HA Core 2021.5 and later brightness detection has changed
        // to simplify things in the app lets use both methods for now
        val supportedColorModes =
            attributes["supported_color_modes"] as? List<String>
        val supportsBrightness =
            if (supportedColorModes ==
                null
            ) {
                false
            } else {
                (supportedColorModes - EntityExt.LIGHT_MODE_NO_BRIGHTNESS_SUPPORT.toSet()).isNotEmpty()
            }
        val supportedFeatures = (attributes["supported_features"] as Number).toInt()
        supportsBrightness ||
            (supportedFeatures and EntityExt.LIGHT_SUPPORT_BRIGHTNESS_DEPR == EntityExt.LIGHT_SUPPORT_BRIGHTNESS_DEPR)
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get supportsLightBrightness")
        false
    }
}

fun Entity.getLightBrightness(): EntityPosition? {
    // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-light.ts#L90
    return try {
        if (!supportsLightBrightness()) return null

        when (state) {
            "on" -> {
                val minValue = 0f
                val maxValue = 100f
                val currentValue =
                    (attributes["brightness"] as? Number)?.toFloat()?.div(255f)
                        ?.times(100)
                        ?: 0f

                EntityPosition(
                    value = currentValue.coerceAtLeast(minValue).coerceAtMost(maxValue),
                    min = minValue,
                    max = maxValue,
                )
            }

            else -> null
        }
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get getLightBrightness")
        null
    }
}

fun Entity.supportsLightColorTemperature(): Boolean {
    return try {
        if (domain != "light") return false

        val supportedColorModes =
            attributes["supported_color_modes"] as? List<String>
        val supportsColorTemp =
            supportedColorModes?.contains(EntityExt.LIGHT_MODE_COLOR_TEMP) == true
        val supportedFeatures = (attributes["supported_features"] as Number).toInt()
        supportsColorTemp ||
            (supportedFeatures and EntityExt.LIGHT_SUPPORT_COLOR_TEMP_DEPR == EntityExt.LIGHT_SUPPORT_COLOR_TEMP_DEPR)
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get supportsLightColorTemperature")
        false
    }
}

fun Entity.getLightColor(): Int? {
    // https://github.com/home-assistant/frontend/blob/dev/src/panels/lovelace/cards/hui-light-card.ts#L243
    return try {
        if (domain != "light") return null

        when {
            state != "off" && attributes["rgb_color"] != null -> {
                val (r, g, b) = (attributes["rgb_color"] as List<Number>).map { it.toInt() }
                Color.rgb(r, g, b)
            }

            else -> null
        }
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get getLightColor")
        null
    }
}

fun Entity.supportsVolumeSet(): Boolean {
    return try {
        if (domain != MEDIA_PLAYER_DOMAIN) return false
        (attributes["supported_features"] as Number).toInt() and
            EntityExt.MEDIA_PLAYER_SUPPORT_VOLUME_SET == EntityExt.MEDIA_PLAYER_SUPPORT_VOLUME_SET
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get supportsVolumeSet")
        false
    }
}

fun Entity.getVolumeLevel(): EntityPosition? {
    return try {
        if (!supportsVolumeSet()) return null

        val minValue = 0f
        val maxValue = 100f

        // Convert to percentage to match frontend behavior:
        // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-media_player.ts#L137
        val currentValue = (attributes["volume_level"] as? Number)?.toFloat()?.times(100) ?: 0f

        EntityPosition(
            value = currentValue.coerceAtLeast(minValue).coerceAtMost(maxValue),
            min = minValue,
            max = maxValue,
        )
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get getVolumeLevel")
        null
    }
}

fun Entity.getVolumeStep(): Float {
    return try {
        if (!supportsVolumeSet()) return 0.1f

        val volumeStep = (attributes["volume_step"] as? Number)?.toFloat() ?: 0.1f
        volumeStep.coerceAtLeast(0.01f)
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get getVolumeStep")
        0.1f
    }
}

fun Entity.getIcon(context: Context): IIcon {
    val attributes = this.attributes
    val icon = attributes["icon"] as? String
    return if (icon?.startsWith("mdi") == true) {
        val mdiIcon = icon.split(":")[1]
        return IconicsDrawable(context, "cmd-$mdiIcon").icon ?: Icon.cmd_bookmark
    } else {
        /**
         * Return a default icon for the domain that matches the icon used in the frontend, see
         * icons.json in the component's core integration.
         * Note: for SimplifiedEntity sometimes return a more general icon because we don't have state.
         */
        val compareState =
            state.ifBlank { attributes["state"] as String? }
        when (domain) {
            "air_quality" -> Icon.cmd_air_filter
            "alarm_control_panel" -> when (compareState) {
                "armed_away" -> Icon3.cmd_shield_lock
                "armed_custom_bypass" -> Icon3.cmd_security
                "armed_home" -> Icon3.cmd_shield_home
                "armed_night" -> Icon3.cmd_shield_moon
                "armed_vacation" -> Icon3.cmd_shield_airplane
                "disarmed" -> Icon3.cmd_shield_off
                "pending" -> Icon3.cmd_shield_outline
                "triggered" -> Icon.cmd_bell_ring
                else -> Icon3.cmd_shield
            }

            "alert" -> Icon.cmd_alert
            "automation" -> if (compareState == "off") {
                Icon3.cmd_robot_off
            } else {
                Icon3.cmd_robot
            }

            "binary_sensor" -> binarySensorIcon(compareState, this)
            "button" -> when (attributes["device_class"]) {
                "restart" -> Icon3.cmd_restart
                "update" -> Icon3.cmd_package_up
                else -> Icon2.cmd_gesture_tap_button
            }

            "calendar" -> Icon.cmd_calendar
            CAMERA_DOMAIN -> if (compareState == "off") {
                Icon3.cmd_video_off
            } else {
                Icon3.cmd_video
            }

            "climate" -> Icon3.cmd_thermostat
            "configurator" -> Icon.cmd_cog
            "conversation" -> Icon3.cmd_microphone_message
            "cover" -> coverIcon(compareState, this)
            "counter" -> Icon.cmd_counter
            "fan" -> if (compareState == "off") {
                Icon2.cmd_fan_off
            } else {
                Icon2.cmd_fan
            }

            "google_assistant" -> Icon2.cmd_google_assistant
            "group" -> Icon2.cmd_google_circles_communities
            "homeassistant" -> Icon2.cmd_home_assistant
            "homekit" -> Icon2.cmd_home_automation
            "humidifier" -> if (compareState == "off") {
                Icon.cmd_air_humidifier_off
            } else {
                Icon.cmd_air_humidifier
            }

            "image_processing" -> Icon2.cmd_image_filter_frames
            "input_boolean" -> if (!entityId.endsWith(".ha_android_placeholder")) {
                if (compareState == "on") {
                    Icon.cmd_check_circle_outline
                } else {
                    Icon.cmd_close_circle_outline
                }
            } else { // For SimplifiedEntity without state, use a more generic icon
                Icon3.cmd_toggle_switch_outline
            }

            "input_button" -> Icon2.cmd_gesture_tap_button
            "input_datetime" -> if (attributes["has_date"] == false) {
                Icon.cmd_clock
            } else if (attributes["has_time"] == false) {
                Icon.cmd_calendar
            } else {
                Icon.cmd_calendar_clock
            }

            "input_number" -> Icon3.cmd_ray_vertex
            "input_select" -> Icon2.cmd_format_list_bulleted
            "input_text" -> Icon2.cmd_form_textbox
            "lawn_mower" -> Icon3.cmd_robot_mower
            "light" -> Icon2.cmd_lightbulb
            "lock" -> when (compareState) {
                "unlocked", "open" -> Icon2.cmd_lock_open_variant
                "jammed" -> Icon2.cmd_lock_alert
                "locking", "unlocking", "opening" -> Icon2.cmd_lock_clock
                else -> Icon2.cmd_lock
            }

            "mailbox" -> Icon3.cmd_mailbox
            MEDIA_PLAYER_DOMAIN -> when (attributes["device_class"]) {
                "speaker" -> when (compareState) {
                    "playing" -> Icon3.cmd_speaker_play
                    "paused" -> Icon3.cmd_speaker_pause
                    "off" -> Icon3.cmd_speaker_off
                    else -> Icon3.cmd_speaker
                }

                "tv" -> when (compareState) {
                    "playing" -> Icon3.cmd_television_play
                    "paused" -> Icon3.cmd_television_pause
                    "off" -> Icon3.cmd_television_off
                    else -> Icon3.cmd_television
                }

                "receiver" -> when (compareState) {
                    "off" -> Icon.cmd_audio_video_off
                    else -> Icon.cmd_audio_video
                }

                else -> when (compareState) {
                    "playing", "paused" -> Icon.cmd_cast_connected
                    "off" -> Icon.cmd_cast_off
                    else -> Icon.cmd_cast
                }
            }

            "notify" -> Icon3.cmd_message
            "number" -> when (attributes["device_class"]) {
                "apparent_power", "power", "reactive_power" -> Icon2.cmd_flash
                "aqi" -> Icon.cmd_air_filter
                "area" -> Icon3.cmd_texture_box
                "atmospheric_pressure" -> Icon3.cmd_thermometer_lines
                "battery" -> Icon.cmd_battery
                "blood_glucose_concentration" -> Icon3.cmd_spoon_sugar
                "carbon_dioxide" -> Icon3.cmd_molecule_co2
                "carbon_monoxide" -> Icon3.cmd_molecule_co
                "conductivity" -> Icon3.cmd_sprout_outline
                "current" -> Icon.cmd_current_ac
                "data_rate" -> Icon3.cmd_transmission_tower
                "data_size" -> Icon.cmd_database
                "distance" -> Icon.cmd_arrow_left_right
                "duration" -> Icon3.cmd_progress_clock
                "energy" -> Icon2.cmd_lightning_bolt
                "energy_storage" -> Icon.cmd_car_battery
                "frequency", "voltage" -> Icon3.cmd_sine_wave
                "gas" -> Icon3.cmd_meter_gas
                "humidity" -> Icon3.cmd_water_percent
                "illuminance" -> Icon.cmd_brightness_5
                "irradiance" -> Icon3.cmd_sun_wireless
                "moisture" -> Icon3.cmd_water_percent
                "monetary" -> Icon.cmd_cash
                "nitrogen_dioxide", "nitrogen_monoxide", "nitrogen_oxide", "ozone",
                "pm1", "pm10", "pm25", "sulfur_dioxide", "volatile_organic_compounds",
                "volatile_organic_compounds_parts",
                -> Icon3.cmd_molecule

                "ph" -> Icon3.cmd_ph
                "power_factor" -> Icon.cmd_angle_acute
                "precipitation" -> Icon3.cmd_weather_rainy
                "precipitation_intensity" -> Icon3.cmd_weather_pouring
                "pressure" -> Icon2.cmd_gauge
                "signal_strength" -> Icon3.cmd_wifi
                "sound_pressure" -> Icon.cmd_ear_hearing
                "speed" -> Icon3.cmd_speedometer
                "temperature" -> Icon3.cmd_thermometer
                "volume" -> Icon.cmd_car_coolant_level
                "volume_storage" -> Icon3.cmd_storage_tank
                "water" -> Icon3.cmd_water
                "weight" -> Icon3.cmd_weight
                "wind_speed" -> Icon3.cmd_weather_windy
                else -> Icon3.cmd_ray_vertex
            }

            "persistent_notification" -> Icon.cmd_bell
            "person" -> if (compareState == "not_home") {
                Icon.cmd_account_arrow_right
            } else {
                Icon.cmd_account
            }

            "plant" -> Icon2.cmd_flower
            "proximity" -> Icon.cmd_apple_safari
            "remote" -> if (compareState == "on") {
                Icon3.cmd_remote
            } else {
                Icon3.cmd_remote_off
            }

            "scene" -> Icon3.cmd_palette_outline // Different from frontend: outline version
            "schedule" -> Icon.cmd_calendar_clock
            "script" -> Icon3.cmd_script_text_outline // Different from frontend: outline version
            "select" -> Icon2.cmd_format_list_bulleted
            "sensor" -> sensorIcon(compareState, this)
            "siren" -> Icon.cmd_bullhorn
            "simple_alarm" -> Icon.cmd_bell
            "sun" -> if (compareState == "above_horizon") {
                Icon3.cmd_white_balance_sunny
            } else {
                Icon3.cmd_weather_night
            }

            "switch" -> if (!entityId.endsWith(".ha_android_placeholder")) {
                when (attributes["device_class"]) {
                    "outlet" -> if (compareState ==
                        "on"
                    ) {
                        Icon3.cmd_power_plug
                    } else {
                        Icon3.cmd_power_plug_off
                    }

                    "switch" -> if (compareState ==
                        "on"
                    ) {
                        Icon3.cmd_toggle_switch_variant
                    } else {
                        Icon3.cmd_toggle_switch_variant_off
                    }

                    else -> Icon2.cmd_flash
                }
            } else { // For SimplifiedEntity without state, use a more generic icon
                Icon2.cmd_light_switch
            }

            "tag" -> Icon3.cmd_tag_outline
            "text" -> Icon2.cmd_form_textbox
            "timer" -> Icon3.cmd_timer_outline
            "update" -> Icon3.cmd_package
            "updater" -> Icon.cmd_cloud_upload
            "vacuum" -> Icon3.cmd_robot_vacuum
            "water_heater" -> if (compareState == "off") {
                Icon3.cmd_water_boiler_off
            } else {
                Icon3.cmd_water_boiler
            }

            "weather" -> when (state) {
                "clear-night" -> Icon3.cmd_weather_night
                "exceptional" -> Icon.cmd_alert_circle_outline
                "fog" -> Icon3.cmd_weather_fog
                "hail" -> Icon3.cmd_weather_hail
                "lightning" -> Icon3.cmd_weather_lightning
                "lightning-rainy" -> Icon3.cmd_weather_lightning_rainy
                "partlycloudy" -> Icon3.cmd_weather_partly_cloudy
                "pouring" -> Icon3.cmd_weather_pouring
                "rainy" -> Icon3.cmd_weather_rainy
                "snowy" -> Icon3.cmd_weather_snowy
                "snowy-rainy" -> Icon3.cmd_weather_snowy_rainy
                "sunny" -> Icon3.cmd_weather_sunny
                "windy" -> Icon3.cmd_weather_windy
                "windy-variant" -> Icon3.cmd_weather_windy_variant
                else -> Icon3.cmd_weather_cloudy
            }

            "zone" -> Icon3.cmd_map_marker_radius
            else -> Icon.cmd_bookmark
        }
    }
}

fun Entity.isUsableInTile(): Boolean {
    return domain in EntityExt.APP_PRESS_ACTION_DOMAINS
}

private fun binarySensorIcon(state: String?, entity: Entity): IIcon {
    val isOff = state == "off"

    return when (entity.attributes["device_class"]) {
        "battery" -> if (isOff) Icon.cmd_battery else Icon.cmd_battery_outline
        "battery_charging" -> if (isOff) Icon.cmd_battery else Icon.cmd_battery_charging
        "carbon_monoxide" -> if (isOff) Icon3.cmd_smoke_detector else Icon3.cmd_smoke_detector_alert
        "cold" -> if (isOff) Icon3.cmd_thermometer else Icon3.cmd_snowflake
        "connectivity" -> if (isOff) Icon.cmd_close_network_outline else Icon.cmd_check_network_outline
        "door" -> if (isOff) Icon.cmd_door_closed else Icon.cmd_door_open
        "garage_door" -> if (isOff) Icon2.cmd_garage else Icon2.cmd_garage_open
        "gas", "problem", "safety", "tamper" -> if (isOff) Icon.cmd_check_circle else Icon.cmd_alert_circle
        "heat" -> if (isOff) Icon3.cmd_thermometer else Icon2.cmd_fire
        "light" -> if (isOff) Icon.cmd_brightness_5 else Icon.cmd_brightness_7
        "lock" -> if (isOff) Icon2.cmd_lock else Icon2.cmd_lock_open
        "moisture" -> if (isOff) Icon3.cmd_water_off else Icon3.cmd_water
        "motion" -> if (isOff) Icon3.cmd_motion_sensor_off else Icon3.cmd_motion_sensor
        "occupancy", "presence" -> if (isOff) Icon2.cmd_home_outline else Icon2.cmd_home
        "opening" -> if (isOff) Icon3.cmd_square else Icon3.cmd_square_outline
        "plug", "power" -> if (isOff) Icon3.cmd_power_plug_off else Icon3.cmd_power_plug
        "running" -> if (isOff) Icon3.cmd_stop else Icon3.cmd_play
        "smoke" -> if (isOff) Icon3.cmd_smoke_detector_variant else Icon3.cmd_smoke_detector_variant_alert
        "sound" -> if (isOff) Icon3.cmd_music_note_off else Icon3.cmd_music_note
        "update" -> if (isOff) Icon3.cmd_package else Icon3.cmd_package_up
        "vibration" -> if (isOff) Icon.cmd_crop_portrait else Icon3.cmd_vibrate
        "window" -> if (isOff) Icon3.cmd_window_closed else Icon3.cmd_window_open
        else -> if (isOff) Icon3.cmd_radiobox_blank else Icon.cmd_checkbox_marked_circle
    }
}

private fun coverIcon(state: String?, entity: Entity): IIcon {
    val open = state !== "closed"

    return when (entity.attributes["device_class"]) {
        "garage" -> when (state) {
            "opening" -> Icon.cmd_arrow_up_box
            "closing" -> Icon.cmd_arrow_down_box
            "closed" -> Icon2.cmd_garage
            else -> Icon2.cmd_garage_open
        }

        "gate" -> when (state) {
            "opening", "closing" -> Icon2.cmd_gate_arrow_right
            "closed" -> Icon2.cmd_gate
            else -> Icon2.cmd_gate_open
        }

        "door" -> if (open) Icon.cmd_door_open else Icon.cmd_door_closed
        "damper" -> if (open) Icon.cmd_circle else Icon.cmd_circle_slice_8
        "shutter" -> when (state) {
            "opening" -> Icon.cmd_arrow_up_box
            "closing" -> Icon.cmd_arrow_down_box
            "closed" -> Icon3.cmd_window_shutter
            else -> Icon3.cmd_window_shutter_open
        }

        "curtain" -> when (state) {
            "opening" -> Icon.cmd_arrow_split_vertical
            "closing" -> Icon.cmd_arrow_collapse_horizontal
            "closed" -> Icon.cmd_curtains_closed
            else -> Icon.cmd_curtains
        }

        "blind", "shade" -> when (state) {
            "opening" -> Icon.cmd_arrow_up_box
            "closing" -> Icon.cmd_arrow_down_box
            "closed" -> Icon.cmd_blinds
            else -> Icon.cmd_blinds_open
        }

        else -> when (state) {
            "opening" -> Icon.cmd_arrow_up_box
            "closing" -> Icon.cmd_arrow_down_box
            "closed" -> Icon3.cmd_window_closed
            else -> Icon3.cmd_window_open
        }
    }
}

private fun sensorIcon(state: String?, entity: Entity): IIcon {
    var icon: IIcon? = null

    if (entity.attributes["device_class"] != null) {
        icon = when (entity.attributes["device_class"]) {
            "apparent_power", "power", "reactive_power" -> Icon2.cmd_flash
            "aqi" -> Icon.cmd_air_filter
            "atmospheric_pressure" -> Icon3.cmd_thermometer_lines
            "battery" -> {
                val batteryValue = state?.toDoubleOrNull()
                if (batteryValue == null) {
                    when (state) {
                        "off" -> Icon.cmd_battery
                        "on" -> Icon.cmd_battery_alert
                        else -> Icon.cmd_battery_unknown
                    }
                } else if (batteryValue <= 5) {
                    Icon.cmd_battery_alert_variant_outline
                } else {
                    when (((batteryValue / 10) * 10).toInt()) {
                        10 -> Icon.cmd_battery_10
                        20 -> Icon.cmd_battery_20
                        30 -> Icon.cmd_battery_30
                        40 -> Icon.cmd_battery_40
                        50 -> Icon.cmd_battery_50
                        60 -> Icon.cmd_battery_60
                        70 -> Icon.cmd_battery_70
                        80 -> Icon.cmd_battery_80
                        90 -> Icon.cmd_battery_90
                        else -> Icon.cmd_battery
                    }
                }
            }

            "carbon_dioxide" -> Icon3.cmd_molecule_co2
            "carbon_monoxide" -> Icon3.cmd_molecule_co
            "current" -> Icon.cmd_current_ac
            "data_rate" -> Icon3.cmd_transmission_tower
            "data_size" -> Icon.cmd_database
            "date" -> Icon.cmd_calendar
            "distance" -> Icon.cmd_arrow_left_right
            "duration" -> Icon3.cmd_progress_clock
            "energy" -> Icon2.cmd_lightning_bolt
            "frequency", "voltage" -> Icon3.cmd_sine_wave
            "gas" -> Icon3.cmd_meter_gas
            "humidity", "moisture" -> Icon3.cmd_water_percent
            "illuminance" -> Icon.cmd_brightness_5
            "irradiance" -> Icon3.cmd_sun_wireless
            "monetary" -> Icon.cmd_cash
            "nitrogen_dioxide",
            "nitrogen_monoxide",
            "nitrous_oxide",
            "ozone",
            "pm1",
            "pm10",
            "pm25",
            "sulphur_dioxide",
            "volatile_organic_compounds",
            -> Icon3.cmd_molecule

            "power_factor" -> Icon.cmd_angle_acute
            "precipitation" -> Icon3.cmd_weather_rainy
            "precipitation_intensity" -> Icon3.cmd_weather_pouring
            "pressure" -> Icon2.cmd_gauge
            "signal_strength" -> Icon3.cmd_wifi
            "sound_pressure" -> Icon.cmd_ear_hearing
            "speed" -> Icon3.cmd_speedometer
            "temperature" -> Icon3.cmd_thermometer
            "timestamp" -> Icon.cmd_clock
            "volume" -> Icon.cmd_car_coolant_level
            "water" -> Icon3.cmd_water
            "weight" -> Icon3.cmd_weight
            "wind_speed" -> Icon3.cmd_weather_windy
            else -> null
        }
    }

    if (icon == null) {
        val unitOfMeasurement = entity.attributes["unit_of_measurement"]
        if (unitOfMeasurement != null && unitOfMeasurement in listOf("°C", "°F")) {
            icon = Icon3.cmd_thermometer
        }
    }

    return icon ?: Icon.cmd_eye
}

suspend fun Entity.onPressed(integrationRepository: IntegrationRepository) {
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
        "switch",
        -> {
            if (state == "on") "turn_off" else "turn_on"
        }

        "scene" -> "turn_on"
        else -> "toggle"
    }

    integrationRepository.callAction(
        domain = this.domain,
        action = action,
        actionData = hashMapOf("entity_id" to entityId),
    )
}

/**
 * Execute an app press action like [Entity.onPressed], but without a current state if possible to
 * speed up the execution.
 * @throws IntegrationException on network errors
 */
suspend fun onEntityPressedWithoutState(entityId: String, integrationRepository: IntegrationRepository) {
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
        actionData = hashMapOf("entity_id" to entityId),
    )
}

val Entity.friendlyName: String
    get() = attributes["friendly_name"]?.toString() ?: entityId

fun Entity.friendlyState(
    context: Context,
    options: EntityRegistryOptions? = null,
    appendUnitOfMeasurement: Boolean = false,
): String {
    val attributes = this.attributes

    var friendlyState = when (domain) {
        "binary_sensor" -> {
            // https://github.com/home-assistant/core/blob/dev/homeassistant/components/binary_sensor/strings.json#L113
            when (attributes["device_class"]) {
                "battery" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_low)
                } else {
                    context.getString(commonR.string.state_normal)
                }

                "battery_charging" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_charging)
                } else {
                    context.getString(commonR.string.state_not_charging)
                }

                "cold" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_cold)
                } else {
                    context.getString(commonR.string.state_off)
                }

                "connectivity" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_connected)
                } else {
                    context.getString(commonR.string.state_disconnected)
                }

                "door", "window", "garage_door", "opening" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_open)
                } else {
                    context.getString(commonR.string.state_closed)
                }

                "gas" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_detected)
                } else {
                    context.getString(commonR.string.state_clear)
                }

                "heat" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_hot)
                } else {
                    context.getString(commonR.string.state_off)
                }

                "light" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_light_detected)
                } else {
                    context.getString(commonR.string.state_no_light)
                }

                "lock" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_unlocked)
                } else {
                    context.getString(commonR.string.state_locked)
                }

                "moisture" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_wet)
                } else {
                    context.getString(commonR.string.state_dry)
                }

                "moving" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_moving)
                } else {
                    context.getString(commonR.string.state_not_moving)
                }

                "plug" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_plugged_in)
                } else {
                    context.getString(commonR.string.state_unplugged)
                }

                "presence" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_home)
                } else {
                    context.getString(commonR.string.state_not_home)
                }

                "problem" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_problem)
                } else {
                    context.getString(commonR.string.state_ok)
                }

                "running" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_running)
                } else {
                    context.getString(commonR.string.state_not_running)
                }

                "safety" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_unsafe)
                } else {
                    context.getString(commonR.string.state_safe)
                }

                "tamper" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_tampering_detected)
                } else {
                    context.getString(commonR.string.state_off)
                }

                "update" -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_update_available)
                } else {
                    context.getString(commonR.string.state_up_to_date)
                }

                else -> if (state ==
                    "on"
                ) {
                    context.getString(commonR.string.state_on)
                } else {
                    context.getString(commonR.string.state_off)
                }
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
                DateUtils.FORMAT_ABBREV_ALL,
            ).toString()
        } catch (e: DateTimeParseException) {
            /* Not a timestamp */
        }
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
        val unit = attributes["unit_of_measurement"]?.toString()

        if (unit?.isNotBlank() == true) {
            return "$friendlyState $unit"
        }
    }

    return friendlyState
}

fun Entity.canSupportPrecision() = domain == "sensor" && state.toDoubleOrNull() != null

fun Entity.isExecuting() = when (state) {
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

fun Entity.isActive() = when {
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
    (domain == MEDIA_PLAYER_DOMAIN) -> state != "standby"
    (domain == "vacuum") -> state !in listOf("idle", "docked", "paused")
    (domain == "plant") -> state == "problem"
    (domain == "group") -> state in listOf("on", "home", "open", "locked", "problem")
    (domain == "timer") -> state == "active"
    (domain == CAMERA_DOMAIN) -> state == "streaming"
    else -> true
}
