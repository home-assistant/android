package io.homeassistant.companion.android.common.data.integration.display

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityCoordinates
import io.homeassistant.companion.android.common.data.integration.EntityPosition
import io.homeassistant.companion.android.common.data.integration.FanControls
import io.homeassistant.companion.android.common.data.integration.FriendlyState
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains
import io.homeassistant.companion.android.common.data.integration.LightControls
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.integration.getAlarmOnPressedAction
import io.homeassistant.companion.android.common.data.integration.getColorTemperature
import io.homeassistant.companion.android.common.data.integration.getCoordinates
import io.homeassistant.companion.android.common.data.integration.getCoverPosition
import io.homeassistant.companion.android.common.data.integration.getFanSpeed
import io.homeassistant.companion.android.common.data.integration.getFanSteps
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.getLightBrightness
import io.homeassistant.companion.android.common.data.integration.getLightColor
import io.homeassistant.companion.android.common.data.integration.getStatelessIcon
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.data.integration.isExecuting
import io.homeassistant.companion.android.common.data.integration.supportsFanSetSpeed
import io.homeassistant.companion.android.common.data.integration.supportsLightBrightness
import java.time.LocalDateTime

private const val CATEGORY_CONFIG = "config"
private const val CATEGORY_DIAGNOSTIC = "diagnostic"

enum class EntityCategory {
    CONFIG,
    DIAGNOSTIC,
    ;

    companion object {
        /** Maps the registry string value (`config`/`diagnostic`) to an [EntityCategory], or null. */
        internal fun fromString(value: String?): EntityCategory? = when (value) {
            CATEGORY_CONFIG -> CONFIG
            CATEGORY_DIAGNOSTIC -> DIAGNOSTIC
            else -> null
        }
    }
}

/** Display information specific to `alarm_control_panel` entities. */
@Immutable
data class AlarmDisplay(
    /** Action to run when the panel is pressed, null when it cannot be acted on. */
    val onPressedAction: String?,
) {
    /** Whether the panel can be acted on. */
    val isActionable: Boolean get() = onPressedAction != null
}

/**
 * Everything to display an entity that its state and the entity registry provide, the single
 * source of truth for entity displays: anything a display needs should be resolved into it at
 * creation.
 *
 * The values are a snapshot at resolution time.
 */
@Immutable
interface EntityDisplay {
    val entityId: String
    val name: String
    val icon: IIcon

    /**
     * Icon of the entity ignoring its state, so it stays the same as the entity changes, for
     * callers persisting an icon reference rather than rendering [icon].
     */
    val statelessIcon: IIcon
    val state: FriendlyState
    val rawState: String

    /** Whether the entity is currently executing an action. */
    val isExecuting: Boolean

    /** Whether the entity is in an active state, for state-colored rendering. */
    val isActive: Boolean

    /** Geographic position of the entity, null when it has none. */
    val coordinates: EntityCoordinates?

    /**
     * Position of the entity within its range (cover position, fan speed, light brightness), null
     * when its domain has none or it is not set.
     */
    val position: EntityPosition?

    /** Color the entity currently displays (the color of a light), null when it has none. */
    val color: Int?

    /** Speed control of the entity, null when it is not a fan supporting one. */
    val fanControls: FanControls?

    /** Controls of the entity, null when it is not a light. */
    val lightControls: LightControls?

    /**
     * When the state of the entity last changed.
     *
     * Changes on every update the server sends for the entity, so an item holding it is rarely
     * equal to the previous one.
     */
    val lastChanged: LocalDateTime?

    /** When the entity was last updated, as chatty as [lastChanged]. */
    val lastUpdated: LocalDateTime?
    val isHidden: Boolean
    val entityCategory: EntityCategory?
    val displayPrecision: Int?

    /** Alarm panel display information. */
    val alarm: AlarmDisplay?
    val labels: List<String>

    val domain: String get() = entityId.substringBefore('.')
}

/** [EntityDisplay] resolved from the entity state and the entity registry only. */
@Immutable
data class EntityDisplayWithoutContext(
    override val entityId: String,
    override val name: String,
    override val icon: IIcon,
    override val statelessIcon: IIcon = icon,
    override val state: FriendlyState = FriendlyState.Literal(""),
    override val rawState: String = "",
    override val isExecuting: Boolean = false,
    override val isActive: Boolean = false,
    override val coordinates: EntityCoordinates? = null,
    override val position: EntityPosition? = null,
    override val color: Int? = null,
    override val fanControls: FanControls? = null,
    override val lightControls: LightControls? = null,
    override val lastChanged: LocalDateTime? = null,
    override val lastUpdated: LocalDateTime? = null,
    override val isHidden: Boolean = false,
    override val entityCategory: EntityCategory? = null,
    override val displayPrecision: Int? = null,
    /** Alarm panel display information, only set for `alarm_control_panel` entities. */
    override val alarm: AlarmDisplay? = null,
    override val labels: List<String> = emptyList(),
) : EntityDisplay {

    /**
     * Resolves the entity-derived fields from an [Entity].
     *
     * [name] defaults to the friendly_name.
     */
    constructor(
        entity: Entity,
        name: String = entity.friendlyName,
        customIcon: IIcon? = null,
        isHidden: Boolean = false,
        entityCategory: EntityCategory? = null,
        displayPrecision: Int? = null,
        labels: List<String> = emptyList(),
    ) : this(
        entityId = entity.entityId,
        name = name,
        icon = customIcon ?: entity.getIcon(),
        statelessIcon = customIcon ?: entity.getStatelessIcon(),
        state = entity.friendlyState(displayPrecision = displayPrecision),
        rawState = entity.state,
        isExecuting = entity.isExecuting(),
        isActive = entity.isActive(),
        coordinates = entity.getCoordinates(),
        position = entity.position(),
        color = entity.getLightColor(),
        fanControls = entity.fanControls(),
        lightControls = entity.lightControls(),
        lastChanged = entity.lastChanged,
        lastUpdated = entity.lastUpdated,
        isHidden = isHidden,
        entityCategory = entityCategory,
        displayPrecision = displayPrecision,
        alarm = entity.alarmDisplay(),
        labels = labels,
    )

    companion object {

        /** Speed control of a fan, null when the entity is not a fan supporting speeds. */
        private fun Entity.fanControls(): FanControls? {
            if (domain != IntegrationDomains.FAN_DOMAIN || !supportsFanSetSpeed()) return null

            val speed = getFanSpeed() ?: return null
            val steps = getFanSteps() ?: return null
            return FanControls(speed = speed, steps = steps)
        }

        /** Controls of a light, null when the entity is not a light. */
        private fun Entity.lightControls(): LightControls? {
            if (domain != IntegrationDomains.LIGHT_DOMAIN) return null

            return LightControls(
                brightness = if (supportsLightBrightness()) getLightBrightness() else null,
                colorTemperature = getColorTemperature(),
            )
        }

        /** Position of the entity for the domains that have one, null for the others. */
        private fun Entity.position(): EntityPosition? = when (domain) {
            IntegrationDomains.COVER_DOMAIN -> getCoverPosition()
            IntegrationDomains.FAN_DOMAIN -> getFanSpeed()
            IntegrationDomains.LIGHT_DOMAIN -> getLightBrightness()
            else -> null
        }

        /** Alarm panel display information, null when the entity is not an alarm control panel. */
        private fun Entity.alarmDisplay(): AlarmDisplay? {
            if (domain != IntegrationDomains.ALARM_CONTROL_PANEL_DOMAIN) return null
            return AlarmDisplay(onPressedAction = getAlarmOnPressedAction())
        }
    }
}

/**
 * [EntityDisplay] with what the area, floor and device registries add to it.
 * A null name is an entity without that data, not data that was
 * never resolved: an item resolved without those registries is an [EntityDisplayWithoutContext].
 */
@Immutable
data class EntityDisplayWithContext(
    private val item: EntityDisplayWithoutContext,
    val areaName: String? = null,
    val floorName: String? = null,
    val deviceName: String? = null,
) : EntityDisplay by item {

    /**
     * Formatted subtitle combining area and device name, adapting the separator to the
     * layout direction. Null if the item has neither, or when it would just repeat [name].
     */
    fun subtitle(layoutDirection: LayoutDirection): String? = listOfNotNull(areaName, deviceName)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(if (layoutDirection == LayoutDirection.Ltr) " ▸ " else " ◂ ")
        ?.takeIf { it != name }
}
