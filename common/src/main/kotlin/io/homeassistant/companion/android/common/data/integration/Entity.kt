package io.homeassistant.companion.android.common.data.integration

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.Immutable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon2
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial.Icon3
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.ALARM_CONTROL_PANEL_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CLIMATE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.COVER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.DEVICE_TRACKER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.FAN_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.LIGHT_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.PERSON_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryOptions
import io.homeassistant.companion.android.common.util.LocalDateTimeSerializer
import io.homeassistant.companion.android.common.util.MDI_PREFIX
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.getIconByMdiName
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/**
 * Validates a JSON element representing an entity state. Returns the string content if valid,
 * empty string if null, or empty string with a warning if the type is not a string.
 */
private fun validateStateElement(element: JsonElement, entityId: String): String = when (element) {
    is JsonPrimitive if element.isString -> element.content
    is JsonNull -> ""
    else -> {
        Timber.w(
            "Entity $entityId state is not a String: $element. Please open an issue on the relevant integration.",
        )
        ""
    }
}

/**
 * Class-level serializer for [Entity] used to partially parse the JSON using a surrogate.
 * It is important to validate the type of the `state` since some custom integrations are
 * not respecting the fact that it should be a string. To avoid crashing while parsing
 * we simply don't parse fully the state and delegate this to the [Entity] constructor.
 */
private object EntitySerializer : KSerializer<Entity> {
    @Serializable
    private data class Surrogate(
        val entityId: String,
        val state: JsonElement,
        @Serializable(with = MapAnySerializer::class)
        val attributes: Map<String, @Polymorphic Any?>,
        @Serializable(with = LocalDateTimeSerializer::class)
        val lastChanged: LocalDateTime,
        @Serializable(with = LocalDateTimeSerializer::class)
        val lastUpdated: LocalDateTime,
    )

    override val descriptor = Surrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Entity) {
        Surrogate.serializer().serialize(
            encoder,
            Surrogate(
                entityId = value.entityId,
                state = JsonPrimitive(value.state),
                attributes = value.attributes,
                lastChanged = value.lastChanged,
                lastUpdated = value.lastUpdated,
            ),
        )
    }

    override fun deserialize(decoder: Decoder): Entity {
        val surrogate = Surrogate.serializer().deserialize(decoder)
        return Entity(
            entityId = surrogate.entityId,
            state = surrogate.state,
            attributes = surrogate.attributes,
            lastChanged = surrogate.lastChanged,
            lastUpdated = surrogate.lastUpdated,
        )
    }
}

@Serializable(with = EntitySerializer::class)
data class Entity(
    val entityId: String,
    val state: String,
    val attributes: Map<String, Any?>,
    val lastChanged: LocalDateTime,
    val lastUpdated: LocalDateTime,
) {
    /**
     * Secondary constructor that accepts a raw [JsonElement] for the state and validates it.
     * If the element is not a JSON string, logs a warning with the [entityId] and falls back
     * to an empty string.
     */
    internal constructor(
        entityId: String,
        state: JsonElement,
        attributes: Map<String, @Polymorphic Any?>,
        lastChanged: LocalDateTime,
        lastUpdated: LocalDateTime,
    ) : this(
        entityId = entityId,
        state = validateStateElement(state, entityId = entityId),
        attributes = attributes,
        lastChanged = lastChanged,
        lastUpdated = lastUpdated,
    )

    /**
     * The domain part of the [entityId] (e.g., "light" from "light.living_room").
     * Lazy to avoid repeated string allocations on each access.
     */
    val domain: String by lazy { entityId.substringBefore('.') }
}

@Immutable
data class EntityPosition(val value: Float, val min: Float, val max: Float)

/** Geographic position of an entity, resolved from its state attributes. */
@Immutable
data class EntityCoordinates(val latitude: Double, val longitude: Double)

/** Speed control of a fan entity, resolved from its state attributes. */
@Immutable
data class FanControls(val speed: EntityPosition, val steps: Int)

/**
 * Color temperature control of a light entity, in kelvin on servers >= 2022.11 and in mireds
 * before, resolved from its state attributes.
 */
@Immutable
data class ColorTemperatureControl(val current: Float, val min: Float, val max: Float, val isKelvin: Boolean)

/** Controls of a light entity, each null when the light does not support it. */
@Immutable
data class LightControls(val brightness: EntityPosition?, val colorTemperature: ColorTemperatureControl?)

/** Controls of a climate entity, resolved from its state attributes, each null when it has none. */
@Immutable
data class ClimateControls(
    val currentTemperature: Float?,
    val targetTemperature: Float?,
    val targetTemperatureStep: Float?,
    val hvacAction: String?,
    val minTemperature: Float?,
    val maxTemperature: Float?,
    val temperatureUnit: String?,
    val hvacModes: List<String>,
    val supportsTargetTemperature: Boolean,
)

/** Value range of a number entity, resolved from its state and attributes. */
@Immutable
data class NumberControls(val range: EntityPosition, val step: Float)

/** Volume control of a media player entity, [volume] null when it cannot be set. */
@Immutable
data class MediaPlayerControls(val volume: EntityPosition?, val volumeStep: Float)

/** Controls of a cover entity, [position] null when it is not set. */
@Immutable
data class CoverControls(val position: EntityPosition?, val supportsSetPosition: Boolean)

/** Controls of a vacuum entity. */
@Immutable
data class VacuumControls(val supportsTurnOn: Boolean)

object EntityExt {
    const val TAG = "EntityExt"

    const val CLIMATE_SUPPORT_TARGET_TEMPERATURE = 1
    const val CLIMATE_SUPPORT_TARGET_TEMPERATURE_RANGE = 2
    const val COVER_SUPPORT_SET_POSITION = 4
    const val FAN_SUPPORT_SET_SPEED = 1
    const val LIGHT_MODE_COLOR_TEMP = "color_temp"
    val LIGHT_MODE_NO_BRIGHTNESS_SUPPORT = listOf("unknown", "onoff")
    const val LIGHT_SUPPORT_BRIGHTNESS_DEPR = 1
    const val LIGHT_SUPPORT_COLOR_TEMP_DEPR = 2
    const val MEDIA_PLAYER_SUPPORT_VOLUME_SET = 4
    const val VACUUM_SUPPORT_TURN_ON = 1

    val DOMAINS_PRESS = listOf("button", "input_button")
    val DOMAINS_TOGGLE = listOf(
        "automation", COVER_DOMAIN, FAN_DOMAIN, "humidifier", "input_boolean", LIGHT_DOMAIN, "lock",
        MEDIA_PLAYER_DOMAIN, "remote", "siren", "switch",
    )

    val APP_PRESS_ACTION_DOMAINS = DOMAINS_PRESS + DOMAINS_TOGGLE + listOf(
        "scene",
        "script",
    )

    val STATE_COLORED_DOMAINS = listOf(
        ALARM_CONTROL_PANEL_DOMAIN,
        "alert",
        "automation",
        "binary_sensor",
        "calendar",
        CAMERA_DOMAIN,
        CLIMATE_DOMAIN,
        COVER_DOMAIN,
        DEVICE_TRACKER_DOMAIN,
        FAN_DOMAIN,
        "group",
        "humidifier",
        "input_boolean",
        "lawn_mower",
        LIGHT_DOMAIN,
        "lock",
        MEDIA_PLAYER_DOMAIN,
        PERSON_DOMAIN,
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

/**
 * Apply a [CompressedStateDiff] to this Entity, and return the [Entity] with updated properties.
 * Based on home-assistant-js-websocket entities `processEvent` function:
 * https://github.com/home-assistant/home-assistant-js-websocket/blob/449fa43668f5316eb31609cd36088c5e82c818e2/lib/entities.ts#L47
 */
fun Entity.applyCompressedStateDiff(diff: CompressedStateDiff): Entity {
    val plus = diff.plus
    val minus = diff.minus

    // Compute new timestamps
    val newLastChanged = plus?.lastChanged
        ?.let { LocalDateTime.ofEpochSecond(round(it).toLong(), 0, ZoneOffset.UTC) }
        ?: lastChanged

    val newLastUpdated = when {
        plus?.lastChanged != null -> newLastChanged
        plus?.lastUpdated != null ->
            LocalDateTime.ofEpochSecond(round(plus.lastUpdated).toLong(), 0, ZoneOffset.UTC)

        else -> lastUpdated
    }

    // Compute new attributes - only create new map if modifications needed
    val hasAttributeChanges = plus?.attributes?.isNotEmpty() == true ||
        minus?.attributes?.isNotEmpty() == true
    val newAttributes = if (hasAttributeChanges) {
        buildMap {
            putAll(attributes)
            plus?.attributes?.let { putAll(it) }
            minus?.attributes?.forEach { remove(it) }
        }
    } else {
        attributes
    }

    val newState = plus?.state
    return if (newState != null) {
        Entity(
            entityId = entityId,
            state = newState,
            attributes = newAttributes,
            lastChanged = newLastChanged,
            lastUpdated = newLastUpdated,
        )
    } else {
        Entity(
            entityId = entityId,
            state = state,
            attributes = newAttributes,
            lastChanged = newLastChanged,
            lastUpdated = newLastUpdated,
        )
    }
}

fun Entity.getCoverPosition(): EntityPosition? {
    // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-cover.ts#L33
    return try {
        if (
            domain != COVER_DOMAIN ||
            attributes["current_position"] == null
        ) {
            return null
        }

        val minValue = 0f
        val maxValue = 100f
        val currentValue = floatAttributeOrNull("current_position") ?: 0f

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

fun Entity.supportsFanSetSpeed(): Boolean = domain == FAN_DOMAIN && supportsFeature(EntityExt.FAN_SUPPORT_SET_SPEED)

fun Entity.getFanSpeed(): EntityPosition? {
    // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-fan.js#L48
    return try {
        if (!supportsFanSetSpeed()) return null

        val minValue = 0f
        val maxValue = 100f
        val currentValue = floatAttributeOrNull("percentage") ?: 0f

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
        if (domain != LIGHT_DOMAIN) return false

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
        supportsBrightness || supportsFeature(EntityExt.LIGHT_SUPPORT_BRIGHTNESS_DEPR)
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
                    floatAttributeOrNull("brightness")?.div(255f)
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
        if (domain != LIGHT_DOMAIN) return false

        val supportedColorModes =
            attributes["supported_color_modes"] as? List<String>
        val supportsColorTemp =
            supportedColorModes?.contains(EntityExt.LIGHT_MODE_COLOR_TEMP) == true
        supportsColorTemp || supportsFeature(EntityExt.LIGHT_SUPPORT_COLOR_TEMP_DEPR)
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get supportsLightColorTemperature")
        false
    }
}

/**
 * Color temperature of a light, null when it doesn't support it or is not currently in that color
 * mode. In kelvin on servers >= 2022.11, in mireds before.
 */
fun Entity.getColorTemperature(): ColorTemperatureControl? {
    if (!supportsLightColorTemperature() || attributes["color_mode"] != EntityExt.LIGHT_MODE_COLOR_TEMP) {
        return null
    }

    // Kelvin was added in 2022.11, older servers only report mireds
    val isKelvin = attributes.containsKey("color_temp_kelvin")
    val min = floatAttributeOrNull(if (isKelvin) "min_color_temp_kelvin" else "min_mireds") ?: 0f
    val max = floatAttributeOrNull(if (isKelvin) "max_color_temp_kelvin" else "max_mireds") ?: 0f
    val current = floatAttributeOrNull(if (isKelvin) "color_temp_kelvin" else "color_temp") ?: 0f

    return ColorTemperatureControl(
        current = current.coerceIn(min, max),
        min = min,
        max = max,
        isKelvin = isKelvin,
    )
}

/** Geographic position of the entity, null when it has none. */
fun Entity.getCoordinates(): EntityCoordinates? {
    val latitude = floatAttributeOrNull("latitude")?.toDouble()
    val longitude = floatAttributeOrNull("longitude")?.toDouble()
    return if (latitude != null && longitude != null) EntityCoordinates(latitude, longitude) else null
}

private fun Entity.floatAttributeOrNull(name: String): Float? = (attributes[name] as? Number)?.toFloat()

/**
 * Whether the entity reports any bit of [feature] in its `supported_features` bitmask, like the
 * frontend `supportsFeature` does.
 */
internal fun Entity.supportsFeature(feature: Int): Boolean =
    ((attributes["supported_features"] as? Number)?.toInt() ?: 0) and feature != 0

/** Controls of a climate entity, null when the entity is not a climate one. */
fun Entity.getClimateControls(): ClimateControls? {
    if (domain != CLIMATE_DOMAIN) return null

    /** Numeric attribute of the entity, accepting both a number and a numeric string, else null. */
    fun Entity.numberAttributeOrNull(name: String): Float? =
        floatAttributeOrNull(name) ?: attributes[name]?.toString()?.toFloatOrNull()

    return ClimateControls(
        currentTemperature = numberAttributeOrNull("current_temperature"),
        targetTemperature = numberAttributeOrNull("temperature"),
        targetTemperatureStep = numberAttributeOrNull("target_temp_step"),
        hvacAction = attributes["hvac_action"]?.toString(),
        minTemperature = numberAttributeOrNull("min_temp"),
        maxTemperature = numberAttributeOrNull("max_temp"),
        temperatureUnit = attributes["temperature_unit"]?.toString(),
        hvacModes = (attributes["hvac_modes"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
        supportsTargetTemperature = supportsFeature(
            EntityExt.CLIMATE_SUPPORT_TARGET_TEMPERATURE or EntityExt.CLIMATE_SUPPORT_TARGET_TEMPERATURE_RANGE,
        ),
    )
}

/** Value range of a number or input_number entity, null for other domains. */
fun Entity.getNumberControls(): NumberControls? {
    if (domain != "number" && domain != "input_number") return null

    return NumberControls(
        range = EntityPosition(
            value = state.toFloatOrNull() ?: 0f,
            min = floatAttributeOrNull("min") ?: 0f,
            max = floatAttributeOrNull("max") ?: 1f,
        ),
        step = floatAttributeOrNull("step") ?: 1f,
    )
}

/** Volume control of a media player entity, null for other domains. */
fun Entity.getMediaPlayerControls(): MediaPlayerControls? {
    if (domain != MEDIA_PLAYER_DOMAIN) return null

    return MediaPlayerControls(
        volume = if (supportsVolumeSet()) getVolumeLevel() else null,
        volumeStep = getVolumeStep(),
    )
}

/** Controls of a cover entity, null for other domains. */
fun Entity.getCoverControls(): CoverControls? {
    if (domain != COVER_DOMAIN) return null

    return CoverControls(
        position = getCoverPosition(),
        supportsSetPosition = supportsFeature(EntityExt.COVER_SUPPORT_SET_POSITION),
    )
}

/** Controls of a vacuum entity, null for other domains. */
fun Entity.getVacuumControls(): VacuumControls? {
    if (domain != "vacuum") return null

    return VacuumControls(
        supportsTurnOn = supportsFeature(EntityExt.VACUUM_SUPPORT_TURN_ON),
    )
}

/** The `device_class` attribute of the entity, or null when it has none. */
fun Entity.deviceClass(): String? = attributes["device_class"] as? String

/** The `entity_picture` attribute of the entity, or null when it has none or it is blank. */
fun Entity.entityPicturePath(): String? = (attributes["entity_picture"] as? String)?.takeIf { it.isNotBlank() }

fun Entity.getLightColor(): Int? {
    // https://github.com/home-assistant/frontend/blob/dev/src/panels/lovelace/cards/hui-light-card.ts#L243
    return try {
        if (domain != LIGHT_DOMAIN) return null

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

fun Entity.supportsVolumeSet(): Boolean = domain == MEDIA_PLAYER_DOMAIN &&
    supportsFeature(EntityExt.MEDIA_PLAYER_SUPPORT_VOLUME_SET)

fun Entity.getVolumeLevel(): EntityPosition? {
    return try {
        if (!supportsVolumeSet()) return null

        val minValue = 0f
        val maxValue = 100f

        // Convert to percentage to match frontend behavior:
        // https://github.com/home-assistant/frontend/blob/dev/src/dialogs/more-info/controls/more-info-media_player.ts#L137
        val currentValue = floatAttributeOrNull("volume_level")?.times(100) ?: 0f

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

        val volumeStep = floatAttributeOrNull("volume_step") ?: 0.1f
        volumeStep.coerceAtLeast(0.01f)
    } catch (e: Exception) {
        Timber.tag(EntityExt.TAG).e(e, "Unable to get getVolumeStep")
        0.1f
    }
}

fun Entity.getIcon(): IIcon = getIcon(
    compareState = state.ifBlank {
        val attributeState = attributes["state"]
        if (attributeState != null && attributeState !is String) {
            Timber.w(
                "Entity $entityId has non-String state attribute: ${attributeState::class.simpleName}. Please open an issue on the relevant integration.",
            )
        }
        attributeState as? String?
    },
)

/**
 * Icon of the entity ignoring its state, so it stays the same as the entity changes: the custom
 * icon it asks for, else the general icon of its domain. For callers persisting an icon rather
 * than rendering the current one with [getIcon].
 */
fun Entity.getStatelessIcon(): IIcon = getIcon(compareState = null)

/**
 * Icon of the entity for [compareState], its state or null to get the icon of the domain that
 * doesn't depend on it. Default icons match the ones used by the frontend, see icons.json in the
 * component's core integration.
 */
private fun Entity.getIcon(compareState: String?): IIcon {
    val attributes = this.attributes
    val icon = attributes["icon"] as? String
    return if (icon?.startsWith(MDI_PREFIX) == true) {
        CommunityMaterial.getIconByMdiName(icon) ?: Icon.cmd_bookmark
    } else {
        when (domain) {
            "air_quality" -> Icon.cmd_air_filter
            ALARM_CONTROL_PANEL_DOMAIN -> when (compareState) {
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

            CLIMATE_DOMAIN -> Icon3.cmd_thermostat
            "configurator" -> Icon.cmd_cog
            "conversation" -> Icon3.cmd_microphone_message
            COVER_DOMAIN -> coverIcon(compareState, this)
            "counter" -> Icon.cmd_counter

            DEVICE_TRACKER_DOMAIN, PERSON_DOMAIN -> if (compareState == "not_home") {
                Icon.cmd_account_arrow_right
            } else {
                Icon.cmd_account
            }

            FAN_DOMAIN -> if (compareState == "off") {
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
            LIGHT_DOMAIN -> Icon2.cmd_lightbulb
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
        LIGHT_DOMAIN -> if (isOff) Icon.cmd_brightness_5 else Icon.cmd_brightness_7
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
    val open = state != "closed"

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
        val unitOfMeasurement = entity.unitOfMeasurement()
        if (unitOfMeasurement != null && unitOfMeasurement in listOf("°C", "°F")) {
            icon = Icon3.cmd_thermometer
        }
    }

    return icon ?: Icon.cmd_eye
}

/**
 * Execute the default app press action, choosing the action from the item's current state.
 * @throws IntegrationException on network errors
 */
suspend fun EntityDisplay.onPressed(integrationRepository: IntegrationRepository) {
    val action = when (domain) {
        "lock" -> {
            if (rawState == "unlocked") "lock" else "unlock"
        }

        ALARM_CONTROL_PANEL_DOMAIN -> alarm?.onPressedAction

        in EntityExt.DOMAINS_PRESS -> "press"
        FAN_DOMAIN,
        "input_boolean",
        "script",
        "switch",
        -> {
            if (rawState == "on") "turn_off" else "turn_on"
        }

        "scene" -> "turn_on"
        else -> "toggle"
    }

    if (action == null) {
        Timber.tag(EntityExt.TAG).w("No action called when entity '%s' was pressed", entityId)
        return
    }

    integrationRepository.callAction(
        domain = domain,
        action = action,
        actionData = hashMapOf("entity_id" to entityId),
    )
}

/**
 * Execute an app press action like [EntityDisplay.onPressed], but without a current state if possible to
 * speed up the execution.
 * @throws IntegrationException on network errors
 */
suspend fun onEntityPressedWithoutState(entityId: String, integrationRepository: IntegrationRepository) {
    val domain = entityId.substringBefore('.')
    val action = when (domain) {
        "lock" -> {
            val lockEntity = try {
                integrationRepository.getEntity(entityId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to get lock entity $entityId")
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

@Deprecated(
    "The friendly name is no longer used for display, as it ignores the entity registry. Resolve the " +
        "display name with EntitiesForDisplayManager, which reads it from EntityDisplay.name.",
)
internal val Entity.friendlyName: String
    get() = attributes["friendly_name"]?.toString()?.takeIf { it.isNotBlank() } ?: entityId

/**
 * Formats the entity state for display without registry context, so no sensor display
 * precision is applied. Prefer `EntityDisplay.state` when the entity has been resolved
 * through `EntitiesForDisplayManager`.
 */
fun Entity.friendlyState(context: Context, appendUnitOfMeasurement: Boolean = false): String =
    friendlyState(displayPrecision = null).resolve(context, withUnit = appendUnitOfMeasurement)

fun Entity.friendlyState(
    context: Context,
    options: EntityRegistryOptions?,
    appendUnitOfMeasurement: Boolean = false,
): String = friendlyState(
    displayPrecision = options?.sensor?.let { it.displayPrecision ?: it.suggestedDisplayPrecision },
).resolve(context, withUnit = appendUnitOfMeasurement)

fun Entity.canSupportPrecision() = domain == "sensor" && state.toDoubleOrNull() != null

/** The unit of measurement of the entity, null when it has none or it is blank. */
fun Entity.unitOfMeasurement(): String? = attributes["unit_of_measurement"]?.toString()?.takeIf {
    it.isNotBlank()
}

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
    (domain == ALARM_CONTROL_PANEL_DOMAIN) -> state != "disarmed"
    (domain == "alert") -> state != "idle"
    (domain == COVER_DOMAIN) -> state != "closed"
    (domain in listOf(DEVICE_TRACKER_DOMAIN, PERSON_DOMAIN)) -> state != "not_home"
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

/**
 * Whether this entity is the `person` entity linked to the user identified by [userId], i.e. a
 * `person` entity whose `user_id` attribute matches.
 */
fun Entity.isPersonOf(userId: String): Boolean = domain == PERSON_DOMAIN && attributes["user_id"] == userId
