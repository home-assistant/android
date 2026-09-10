package io.homeassistant.companion.android.common.data.integration

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.Immutable
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.Account
import io.github.timoptr.mdiicons.generated.AccountArrowRight
import io.github.timoptr.mdiicons.generated.AirFilter
import io.github.timoptr.mdiicons.generated.AirHumidifier
import io.github.timoptr.mdiicons.generated.AirHumidifierOff
import io.github.timoptr.mdiicons.generated.Alert
import io.github.timoptr.mdiicons.generated.AlertCircle
import io.github.timoptr.mdiicons.generated.AlertCircleOutline
import io.github.timoptr.mdiicons.generated.AngleAcute
import io.github.timoptr.mdiicons.generated.AppleSafari
import io.github.timoptr.mdiicons.generated.ArrowCollapseHorizontal
import io.github.timoptr.mdiicons.generated.ArrowDownBox
import io.github.timoptr.mdiicons.generated.ArrowLeftRight
import io.github.timoptr.mdiicons.generated.ArrowSplitVertical
import io.github.timoptr.mdiicons.generated.ArrowUpBox
import io.github.timoptr.mdiicons.generated.AudioVideo
import io.github.timoptr.mdiicons.generated.AudioVideoOff
import io.github.timoptr.mdiicons.generated.Battery
import io.github.timoptr.mdiicons.generated.Battery10
import io.github.timoptr.mdiicons.generated.Battery20
import io.github.timoptr.mdiicons.generated.Battery30
import io.github.timoptr.mdiicons.generated.Battery40
import io.github.timoptr.mdiicons.generated.Battery50
import io.github.timoptr.mdiicons.generated.Battery60
import io.github.timoptr.mdiicons.generated.Battery70
import io.github.timoptr.mdiicons.generated.Battery80
import io.github.timoptr.mdiicons.generated.Battery90
import io.github.timoptr.mdiicons.generated.BatteryAlert
import io.github.timoptr.mdiicons.generated.BatteryAlertVariantOutline
import io.github.timoptr.mdiicons.generated.BatteryCharging
import io.github.timoptr.mdiicons.generated.BatteryOutline
import io.github.timoptr.mdiicons.generated.BatteryUnknown
import io.github.timoptr.mdiicons.generated.Bell
import io.github.timoptr.mdiicons.generated.BellRing
import io.github.timoptr.mdiicons.generated.Blinds
import io.github.timoptr.mdiicons.generated.BlindsOpen
import io.github.timoptr.mdiicons.generated.Bookmark
import io.github.timoptr.mdiicons.generated.Brightness5
import io.github.timoptr.mdiicons.generated.Brightness7
import io.github.timoptr.mdiicons.generated.Bullhorn
import io.github.timoptr.mdiicons.generated.Calendar
import io.github.timoptr.mdiicons.generated.CalendarClock
import io.github.timoptr.mdiicons.generated.CarBattery
import io.github.timoptr.mdiicons.generated.CarCoolantLevel
import io.github.timoptr.mdiicons.generated.Cash
import io.github.timoptr.mdiicons.generated.Cast
import io.github.timoptr.mdiicons.generated.CastConnected
import io.github.timoptr.mdiicons.generated.CastOff
import io.github.timoptr.mdiicons.generated.CheckCircle
import io.github.timoptr.mdiicons.generated.CheckCircleOutline
import io.github.timoptr.mdiicons.generated.CheckNetworkOutline
import io.github.timoptr.mdiicons.generated.CheckboxMarkedCircle
import io.github.timoptr.mdiicons.generated.Circle
import io.github.timoptr.mdiicons.generated.CircleSlice8
import io.github.timoptr.mdiicons.generated.Clock
import io.github.timoptr.mdiicons.generated.CloseCircleOutline
import io.github.timoptr.mdiicons.generated.CloseNetworkOutline
import io.github.timoptr.mdiicons.generated.CloudUpload
import io.github.timoptr.mdiicons.generated.Cog
import io.github.timoptr.mdiicons.generated.Counter
import io.github.timoptr.mdiicons.generated.CropPortrait
import io.github.timoptr.mdiicons.generated.CurrentAc
import io.github.timoptr.mdiicons.generated.Curtains
import io.github.timoptr.mdiicons.generated.CurtainsClosed
import io.github.timoptr.mdiicons.generated.Database
import io.github.timoptr.mdiicons.generated.DoorClosed
import io.github.timoptr.mdiicons.generated.DoorOpen
import io.github.timoptr.mdiicons.generated.EarHearing
import io.github.timoptr.mdiicons.generated.Eye
import io.github.timoptr.mdiicons.generated.Fan
import io.github.timoptr.mdiicons.generated.FanOff
import io.github.timoptr.mdiicons.generated.Fire
import io.github.timoptr.mdiicons.generated.Flash
import io.github.timoptr.mdiicons.generated.Flower
import io.github.timoptr.mdiicons.generated.FormTextbox
import io.github.timoptr.mdiicons.generated.FormatListBulleted
import io.github.timoptr.mdiicons.generated.Garage
import io.github.timoptr.mdiicons.generated.GarageOpen
import io.github.timoptr.mdiicons.generated.Gate
import io.github.timoptr.mdiicons.generated.GateArrowRight
import io.github.timoptr.mdiicons.generated.GateOpen
import io.github.timoptr.mdiicons.generated.Gauge
import io.github.timoptr.mdiicons.generated.GestureTapButton
import io.github.timoptr.mdiicons.generated.GoogleAssistant
import io.github.timoptr.mdiicons.generated.GoogleCirclesCommunities
import io.github.timoptr.mdiicons.generated.Home
import io.github.timoptr.mdiicons.generated.HomeAssistant
import io.github.timoptr.mdiicons.generated.HomeAutomation
import io.github.timoptr.mdiicons.generated.HomeOutline
import io.github.timoptr.mdiicons.generated.ImageFilterFrames
import io.github.timoptr.mdiicons.generated.LightSwitch
import io.github.timoptr.mdiicons.generated.Lightbulb
import io.github.timoptr.mdiicons.generated.LightningBolt
import io.github.timoptr.mdiicons.generated.Lock
import io.github.timoptr.mdiicons.generated.LockAlert
import io.github.timoptr.mdiicons.generated.LockClock
import io.github.timoptr.mdiicons.generated.LockOpen
import io.github.timoptr.mdiicons.generated.LockOpenVariant
import io.github.timoptr.mdiicons.generated.Mailbox
import io.github.timoptr.mdiicons.generated.MapMarkerRadius
import io.github.timoptr.mdiicons.generated.Message
import io.github.timoptr.mdiicons.generated.MeterGas
import io.github.timoptr.mdiicons.generated.MicrophoneMessage
import io.github.timoptr.mdiicons.generated.Molecule
import io.github.timoptr.mdiicons.generated.MoleculeCo
import io.github.timoptr.mdiicons.generated.MoleculeCo2
import io.github.timoptr.mdiicons.generated.MotionSensor
import io.github.timoptr.mdiicons.generated.MotionSensorOff
import io.github.timoptr.mdiicons.generated.MusicNote
import io.github.timoptr.mdiicons.generated.MusicNoteOff
import io.github.timoptr.mdiicons.generated.Package
import io.github.timoptr.mdiicons.generated.PackageUp
import io.github.timoptr.mdiicons.generated.PaletteOutline
import io.github.timoptr.mdiicons.generated.Ph
import io.github.timoptr.mdiicons.generated.Play
import io.github.timoptr.mdiicons.generated.PowerPlug
import io.github.timoptr.mdiicons.generated.PowerPlugOff
import io.github.timoptr.mdiicons.generated.ProgressClock
import io.github.timoptr.mdiicons.generated.RadioboxBlank
import io.github.timoptr.mdiicons.generated.RayVertex
import io.github.timoptr.mdiicons.generated.Remote
import io.github.timoptr.mdiicons.generated.RemoteOff
import io.github.timoptr.mdiicons.generated.Restart
import io.github.timoptr.mdiicons.generated.Robot
import io.github.timoptr.mdiicons.generated.RobotMower
import io.github.timoptr.mdiicons.generated.RobotOff
import io.github.timoptr.mdiicons.generated.RobotVacuum
import io.github.timoptr.mdiicons.generated.ScriptTextOutline
import io.github.timoptr.mdiicons.generated.Security
import io.github.timoptr.mdiicons.generated.Shield
import io.github.timoptr.mdiicons.generated.ShieldAirplane
import io.github.timoptr.mdiicons.generated.ShieldHome
import io.github.timoptr.mdiicons.generated.ShieldLock
import io.github.timoptr.mdiicons.generated.ShieldMoon
import io.github.timoptr.mdiicons.generated.ShieldOff
import io.github.timoptr.mdiicons.generated.ShieldOutline
import io.github.timoptr.mdiicons.generated.SineWave
import io.github.timoptr.mdiicons.generated.SmokeDetector
import io.github.timoptr.mdiicons.generated.SmokeDetectorAlert
import io.github.timoptr.mdiicons.generated.SmokeDetectorVariant
import io.github.timoptr.mdiicons.generated.SmokeDetectorVariantAlert
import io.github.timoptr.mdiicons.generated.Snowflake
import io.github.timoptr.mdiicons.generated.Speaker
import io.github.timoptr.mdiicons.generated.SpeakerOff
import io.github.timoptr.mdiicons.generated.SpeakerPause
import io.github.timoptr.mdiicons.generated.SpeakerPlay
import io.github.timoptr.mdiicons.generated.Speedometer
import io.github.timoptr.mdiicons.generated.SpoonSugar
import io.github.timoptr.mdiicons.generated.SproutOutline
import io.github.timoptr.mdiicons.generated.Square
import io.github.timoptr.mdiicons.generated.SquareOutline
import io.github.timoptr.mdiicons.generated.Stop
import io.github.timoptr.mdiicons.generated.StorageTank
import io.github.timoptr.mdiicons.generated.SunWireless
import io.github.timoptr.mdiicons.generated.TagOutline
import io.github.timoptr.mdiicons.generated.Television
import io.github.timoptr.mdiicons.generated.TelevisionOff
import io.github.timoptr.mdiicons.generated.TelevisionPause
import io.github.timoptr.mdiicons.generated.TelevisionPlay
import io.github.timoptr.mdiicons.generated.TextureBox
import io.github.timoptr.mdiicons.generated.Thermometer
import io.github.timoptr.mdiicons.generated.ThermometerLines
import io.github.timoptr.mdiicons.generated.Thermostat
import io.github.timoptr.mdiicons.generated.TimerOutline
import io.github.timoptr.mdiicons.generated.ToggleSwitchOutline
import io.github.timoptr.mdiicons.generated.ToggleSwitchVariant
import io.github.timoptr.mdiicons.generated.ToggleSwitchVariantOff
import io.github.timoptr.mdiicons.generated.TransmissionTower
import io.github.timoptr.mdiicons.generated.Vibrate
import io.github.timoptr.mdiicons.generated.Video
import io.github.timoptr.mdiicons.generated.VideoOff
import io.github.timoptr.mdiicons.generated.Water
import io.github.timoptr.mdiicons.generated.WaterBoiler
import io.github.timoptr.mdiicons.generated.WaterBoilerOff
import io.github.timoptr.mdiicons.generated.WaterOff
import io.github.timoptr.mdiicons.generated.WaterPercent
import io.github.timoptr.mdiicons.generated.WeatherCloudy
import io.github.timoptr.mdiicons.generated.WeatherFog
import io.github.timoptr.mdiicons.generated.WeatherHail
import io.github.timoptr.mdiicons.generated.WeatherLightning
import io.github.timoptr.mdiicons.generated.WeatherLightningRainy
import io.github.timoptr.mdiicons.generated.WeatherNight
import io.github.timoptr.mdiicons.generated.WeatherPartlyCloudy
import io.github.timoptr.mdiicons.generated.WeatherPouring
import io.github.timoptr.mdiicons.generated.WeatherRainy
import io.github.timoptr.mdiicons.generated.WeatherSnowy
import io.github.timoptr.mdiicons.generated.WeatherSnowyRainy
import io.github.timoptr.mdiicons.generated.WeatherSunny
import io.github.timoptr.mdiicons.generated.WeatherWindy
import io.github.timoptr.mdiicons.generated.WeatherWindyVariant
import io.github.timoptr.mdiicons.generated.Weight
import io.github.timoptr.mdiicons.generated.WhiteBalanceSunny
import io.github.timoptr.mdiicons.generated.Wifi
import io.github.timoptr.mdiicons.generated.WindowClosed
import io.github.timoptr.mdiicons.generated.WindowOpen
import io.github.timoptr.mdiicons.generated.WindowShutter
import io.github.timoptr.mdiicons.generated.WindowShutterOpen
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
import io.homeassistant.companion.android.common.util.fromHaName
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

/**
 * Controls of a camera entity. [entityPicturePath] updates when the camera is controlled, like
 * taking a snapshot of a live stream or refreshing.
 */
@Immutable
data class CameraControls(val entityPicturePath: String?)

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

/** Controls of a camera entity, null for other domains. */
fun Entity.getCameraControls(): CameraControls? {
    if (domain != CAMERA_DOMAIN) return null

    return CameraControls(
        entityPicturePath = entityPicturePath(),
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

fun Entity.getIcon(): MdiIcon = getIcon(
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
fun Entity.getStatelessIcon(): MdiIcon = getIcon(compareState = null)

/**
 * Icon of the entity for [compareState], its state or null to get the icon of the domain that
 * doesn't depend on it. Default icons match the ones used by the frontend, see icons.json in the
 * component's core integration.
 */
private fun Entity.getIcon(compareState: String?): MdiIcon {
    val attributes = this.attributes
    val icon = attributes["icon"] as? String
    return if (icon?.startsWith(MDI_PREFIX) == true) {
        Mdi.fromHaName(icon) ?: Mdi.Bookmark
    } else {
        when (domain) {
            "air_quality" -> Mdi.AirFilter
            ALARM_CONTROL_PANEL_DOMAIN -> when (compareState) {
                "armed_away" -> Mdi.ShieldLock
                "armed_custom_bypass" -> Mdi.Security
                "armed_home" -> Mdi.ShieldHome
                "armed_night" -> Mdi.ShieldMoon
                "armed_vacation" -> Mdi.ShieldAirplane
                "disarmed" -> Mdi.ShieldOff
                "pending" -> Mdi.ShieldOutline
                "triggered" -> Mdi.BellRing
                else -> Mdi.Shield
            }

            "alert" -> Mdi.Alert
            "automation" -> if (compareState == "off") {
                Mdi.RobotOff
            } else {
                Mdi.Robot
            }

            "binary_sensor" -> binarySensorIcon(compareState, this)
            "button" -> when (attributes["device_class"]) {
                "restart" -> Mdi.Restart
                "update" -> Mdi.PackageUp
                else -> Mdi.GestureTapButton
            }

            "calendar" -> Mdi.Calendar
            CAMERA_DOMAIN -> if (compareState == "off") {
                Mdi.VideoOff
            } else {
                Mdi.Video
            }

            CLIMATE_DOMAIN -> Mdi.Thermostat
            "configurator" -> Mdi.Cog
            "conversation" -> Mdi.MicrophoneMessage
            COVER_DOMAIN -> coverIcon(compareState, this)
            "counter" -> Mdi.Counter

            DEVICE_TRACKER_DOMAIN, PERSON_DOMAIN -> if (compareState == "not_home") {
                Mdi.AccountArrowRight
            } else {
                Mdi.Account
            }

            FAN_DOMAIN -> if (compareState == "off") {
                Mdi.FanOff
            } else {
                Mdi.Fan
            }

            "google_assistant" -> Mdi.GoogleAssistant
            "group" -> Mdi.GoogleCirclesCommunities
            "homeassistant" -> Mdi.HomeAssistant
            "homekit" -> Mdi.HomeAutomation
            "humidifier" -> if (compareState == "off") {
                Mdi.AirHumidifierOff
            } else {
                Mdi.AirHumidifier
            }

            "image_processing" -> Mdi.ImageFilterFrames
            "input_boolean" -> if (!entityId.endsWith(".ha_android_placeholder")) {
                if (compareState == "on") {
                    Mdi.CheckCircleOutline
                } else {
                    Mdi.CloseCircleOutline
                }
            } else { // For SimplifiedEntity without state, use a more generic icon
                Mdi.ToggleSwitchOutline
            }

            "input_button" -> Mdi.GestureTapButton
            "input_datetime" -> if (attributes["has_date"] == false) {
                Mdi.Clock
            } else if (attributes["has_time"] == false) {
                Mdi.Calendar
            } else {
                Mdi.CalendarClock
            }

            "input_number" -> Mdi.RayVertex
            "input_select" -> Mdi.FormatListBulleted
            "input_text" -> Mdi.FormTextbox
            "lawn_mower" -> Mdi.RobotMower
            LIGHT_DOMAIN -> Mdi.Lightbulb
            "lock" -> when (compareState) {
                "unlocked", "open" -> Mdi.LockOpenVariant
                "jammed" -> Mdi.LockAlert
                "locking", "unlocking", "opening" -> Mdi.LockClock
                else -> Mdi.Lock
            }

            "mailbox" -> Mdi.Mailbox
            MEDIA_PLAYER_DOMAIN -> when (attributes["device_class"]) {
                "speaker" -> when (compareState) {
                    "playing" -> Mdi.SpeakerPlay
                    "paused" -> Mdi.SpeakerPause
                    "off" -> Mdi.SpeakerOff
                    else -> Mdi.Speaker
                }

                "tv" -> when (compareState) {
                    "playing" -> Mdi.TelevisionPlay
                    "paused" -> Mdi.TelevisionPause
                    "off" -> Mdi.TelevisionOff
                    else -> Mdi.Television
                }

                "receiver" -> when (compareState) {
                    "off" -> Mdi.AudioVideoOff
                    else -> Mdi.AudioVideo
                }

                else -> when (compareState) {
                    "playing", "paused" -> Mdi.CastConnected
                    "off" -> Mdi.CastOff
                    else -> Mdi.Cast
                }
            }

            "notify" -> Mdi.Message
            "number" -> when (attributes["device_class"]) {
                "apparent_power", "power", "reactive_power" -> Mdi.Flash
                "aqi" -> Mdi.AirFilter
                "area" -> Mdi.TextureBox
                "atmospheric_pressure" -> Mdi.ThermometerLines
                "battery" -> Mdi.Battery
                "blood_glucose_concentration" -> Mdi.SpoonSugar
                "carbon_dioxide" -> Mdi.MoleculeCo2
                "carbon_monoxide" -> Mdi.MoleculeCo
                "conductivity" -> Mdi.SproutOutline
                "current" -> Mdi.CurrentAc
                "data_rate" -> Mdi.TransmissionTower
                "data_size" -> Mdi.Database
                "distance" -> Mdi.ArrowLeftRight
                "duration" -> Mdi.ProgressClock
                "energy" -> Mdi.LightningBolt
                "energy_storage" -> Mdi.CarBattery
                "frequency", "voltage" -> Mdi.SineWave
                "gas" -> Mdi.MeterGas
                "humidity" -> Mdi.WaterPercent
                "illuminance" -> Mdi.Brightness5
                "irradiance" -> Mdi.SunWireless
                "moisture" -> Mdi.WaterPercent
                "monetary" -> Mdi.Cash
                "nitrogen_dioxide", "nitrogen_monoxide", "nitrogen_oxide", "ozone",
                "pm1", "pm10", "pm25", "sulfur_dioxide", "volatile_organic_compounds",
                "volatile_organic_compounds_parts",
                -> Mdi.Molecule

                "ph" -> Mdi.Ph
                "power_factor" -> Mdi.AngleAcute
                "precipitation" -> Mdi.WeatherRainy
                "precipitation_intensity" -> Mdi.WeatherPouring
                "pressure" -> Mdi.Gauge
                "signal_strength" -> Mdi.Wifi
                "sound_pressure" -> Mdi.EarHearing
                "speed" -> Mdi.Speedometer
                "temperature" -> Mdi.Thermometer
                "volume" -> Mdi.CarCoolantLevel
                "volume_storage" -> Mdi.StorageTank
                "water" -> Mdi.Water
                "weight" -> Mdi.Weight
                "wind_speed" -> Mdi.WeatherWindy
                else -> Mdi.RayVertex
            }

            "persistent_notification" -> Mdi.Bell

            "plant" -> Mdi.Flower
            "proximity" -> Mdi.AppleSafari
            "remote" -> if (compareState == "on") {
                Mdi.Remote
            } else {
                Mdi.RemoteOff
            }

            "scene" -> Mdi.PaletteOutline // Different from frontend: outline version
            "schedule" -> Mdi.CalendarClock
            "script" -> Mdi.ScriptTextOutline // Different from frontend: outline version
            "select" -> Mdi.FormatListBulleted
            "sensor" -> sensorIcon(compareState, this)
            "siren" -> Mdi.Bullhorn
            "simple_alarm" -> Mdi.Bell
            "sun" -> if (compareState == "above_horizon") {
                Mdi.WhiteBalanceSunny
            } else {
                Mdi.WeatherNight
            }

            "switch" -> if (!entityId.endsWith(".ha_android_placeholder")) {
                when (attributes["device_class"]) {
                    "outlet" -> if (compareState ==
                        "on"
                    ) {
                        Mdi.PowerPlug
                    } else {
                        Mdi.PowerPlugOff
                    }

                    "switch" -> if (compareState ==
                        "on"
                    ) {
                        Mdi.ToggleSwitchVariant
                    } else {
                        Mdi.ToggleSwitchVariantOff
                    }

                    else -> Mdi.Flash
                }
            } else { // For SimplifiedEntity without state, use a more generic icon
                Mdi.LightSwitch
            }

            "tag" -> Mdi.TagOutline
            "text" -> Mdi.FormTextbox
            "timer" -> Mdi.TimerOutline
            "update" -> Mdi.Package
            "updater" -> Mdi.CloudUpload
            "vacuum" -> Mdi.RobotVacuum
            "water_heater" -> if (compareState == "off") {
                Mdi.WaterBoilerOff
            } else {
                Mdi.WaterBoiler
            }

            "weather" -> when (state) {
                "clear-night" -> Mdi.WeatherNight
                "exceptional" -> Mdi.AlertCircleOutline
                "fog" -> Mdi.WeatherFog
                "hail" -> Mdi.WeatherHail
                "lightning" -> Mdi.WeatherLightning
                "lightning-rainy" -> Mdi.WeatherLightningRainy
                "partlycloudy" -> Mdi.WeatherPartlyCloudy
                "pouring" -> Mdi.WeatherPouring
                "rainy" -> Mdi.WeatherRainy
                "snowy" -> Mdi.WeatherSnowy
                "snowy-rainy" -> Mdi.WeatherSnowyRainy
                "sunny" -> Mdi.WeatherSunny
                "windy" -> Mdi.WeatherWindy
                "windy-variant" -> Mdi.WeatherWindyVariant
                else -> Mdi.WeatherCloudy
            }

            "zone" -> Mdi.MapMarkerRadius
            else -> Mdi.Bookmark
        }
    }
}

fun Entity.isUsableInTile(): Boolean {
    return domain in EntityExt.APP_PRESS_ACTION_DOMAINS
}

private fun binarySensorIcon(state: String?, entity: Entity): MdiIcon {
    val isOff = state == "off"

    return when (entity.attributes["device_class"]) {
        "battery" -> if (isOff) Mdi.Battery else Mdi.BatteryOutline
        "battery_charging" -> if (isOff) Mdi.Battery else Mdi.BatteryCharging
        "carbon_monoxide" -> if (isOff) Mdi.SmokeDetector else Mdi.SmokeDetectorAlert
        "cold" -> if (isOff) Mdi.Thermometer else Mdi.Snowflake
        "connectivity" -> if (isOff) Mdi.CloseNetworkOutline else Mdi.CheckNetworkOutline
        "door" -> if (isOff) Mdi.DoorClosed else Mdi.DoorOpen
        "garage_door" -> if (isOff) Mdi.Garage else Mdi.GarageOpen
        "gas", "problem", "safety", "tamper" -> if (isOff) Mdi.CheckCircle else Mdi.AlertCircle
        "heat" -> if (isOff) Mdi.Thermometer else Mdi.Fire
        LIGHT_DOMAIN -> if (isOff) Mdi.Brightness5 else Mdi.Brightness7
        "lock" -> if (isOff) Mdi.Lock else Mdi.LockOpen
        "moisture" -> if (isOff) Mdi.WaterOff else Mdi.Water
        "motion" -> if (isOff) Mdi.MotionSensorOff else Mdi.MotionSensor
        "occupancy", "presence" -> if (isOff) Mdi.HomeOutline else Mdi.Home
        "opening" -> if (isOff) Mdi.Square else Mdi.SquareOutline
        "plug", "power" -> if (isOff) Mdi.PowerPlugOff else Mdi.PowerPlug
        "running" -> if (isOff) Mdi.Stop else Mdi.Play
        "smoke" -> if (isOff) Mdi.SmokeDetectorVariant else Mdi.SmokeDetectorVariantAlert
        "sound" -> if (isOff) Mdi.MusicNoteOff else Mdi.MusicNote
        "update" -> if (isOff) Mdi.Package else Mdi.PackageUp
        "vibration" -> if (isOff) Mdi.CropPortrait else Mdi.Vibrate
        "window" -> if (isOff) Mdi.WindowClosed else Mdi.WindowOpen
        else -> if (isOff) Mdi.RadioboxBlank else Mdi.CheckboxMarkedCircle
    }
}

private fun coverIcon(state: String?, entity: Entity): MdiIcon {
    val open = state != "closed"

    return when (entity.attributes["device_class"]) {
        "garage" -> when (state) {
            "opening" -> Mdi.ArrowUpBox
            "closing" -> Mdi.ArrowDownBox
            "closed" -> Mdi.Garage
            else -> Mdi.GarageOpen
        }

        "gate" -> when (state) {
            "opening", "closing" -> Mdi.GateArrowRight
            "closed" -> Mdi.Gate
            else -> Mdi.GateOpen
        }

        "door" -> if (open) Mdi.DoorOpen else Mdi.DoorClosed
        "damper" -> if (open) Mdi.Circle else Mdi.CircleSlice8
        "shutter" -> when (state) {
            "opening" -> Mdi.ArrowUpBox
            "closing" -> Mdi.ArrowDownBox
            "closed" -> Mdi.WindowShutter
            else -> Mdi.WindowShutterOpen
        }

        "curtain" -> when (state) {
            "opening" -> Mdi.ArrowSplitVertical
            "closing" -> Mdi.ArrowCollapseHorizontal
            "closed" -> Mdi.CurtainsClosed
            else -> Mdi.Curtains
        }

        "blind", "shade" -> when (state) {
            "opening" -> Mdi.ArrowUpBox
            "closing" -> Mdi.ArrowDownBox
            "closed" -> Mdi.Blinds
            else -> Mdi.BlindsOpen
        }

        else -> when (state) {
            "opening" -> Mdi.ArrowUpBox
            "closing" -> Mdi.ArrowDownBox
            "closed" -> Mdi.WindowClosed
            else -> Mdi.WindowOpen
        }
    }
}

private fun sensorIcon(state: String?, entity: Entity): MdiIcon {
    var icon: MdiIcon? = null

    if (entity.attributes["device_class"] != null) {
        icon = when (entity.attributes["device_class"]) {
            "apparent_power", "power", "reactive_power" -> Mdi.Flash
            "aqi" -> Mdi.AirFilter
            "atmospheric_pressure" -> Mdi.ThermometerLines
            "battery" -> {
                val batteryValue = state?.toDoubleOrNull()
                if (batteryValue == null) {
                    when (state) {
                        "off" -> Mdi.Battery
                        "on" -> Mdi.BatteryAlert
                        else -> Mdi.BatteryUnknown
                    }
                } else if (batteryValue <= 5) {
                    Mdi.BatteryAlertVariantOutline
                } else {
                    when (((batteryValue / 10) * 10).toInt()) {
                        10 -> Mdi.Battery10
                        20 -> Mdi.Battery20
                        30 -> Mdi.Battery30
                        40 -> Mdi.Battery40
                        50 -> Mdi.Battery50
                        60 -> Mdi.Battery60
                        70 -> Mdi.Battery70
                        80 -> Mdi.Battery80
                        90 -> Mdi.Battery90
                        else -> Mdi.Battery
                    }
                }
            }

            "carbon_dioxide" -> Mdi.MoleculeCo2
            "carbon_monoxide" -> Mdi.MoleculeCo
            "current" -> Mdi.CurrentAc
            "data_rate" -> Mdi.TransmissionTower
            "data_size" -> Mdi.Database
            "date" -> Mdi.Calendar
            "distance" -> Mdi.ArrowLeftRight
            "duration" -> Mdi.ProgressClock
            "energy" -> Mdi.LightningBolt
            "frequency", "voltage" -> Mdi.SineWave
            "gas" -> Mdi.MeterGas
            "humidity", "moisture" -> Mdi.WaterPercent
            "illuminance" -> Mdi.Brightness5
            "irradiance" -> Mdi.SunWireless
            "monetary" -> Mdi.Cash
            "nitrogen_dioxide",
            "nitrogen_monoxide",
            "nitrous_oxide",
            "ozone",
            "pm1",
            "pm10",
            "pm25",
            "sulphur_dioxide",
            "volatile_organic_compounds",
            -> Mdi.Molecule

            "power_factor" -> Mdi.AngleAcute
            "precipitation" -> Mdi.WeatherRainy
            "precipitation_intensity" -> Mdi.WeatherPouring
            "pressure" -> Mdi.Gauge
            "signal_strength" -> Mdi.Wifi
            "sound_pressure" -> Mdi.EarHearing
            "speed" -> Mdi.Speedometer
            "temperature" -> Mdi.Thermometer
            "timestamp" -> Mdi.Clock
            "volume" -> Mdi.CarCoolantLevel
            "water" -> Mdi.Water
            "weight" -> Mdi.Weight
            "wind_speed" -> Mdi.WeatherWindy
            else -> null
        }
    }

    if (icon == null) {
        val unitOfMeasurement = entity.unitOfMeasurement()
        if (unitOfMeasurement != null && unitOfMeasurement in listOf("°C", "°F")) {
            icon = Mdi.Thermometer
        }
    }

    return icon ?: Mdi.Eye
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
