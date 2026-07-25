package io.homeassistant.companion.android.common.data.integration.display

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.FriendlyState
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.isActive
import io.homeassistant.companion.android.common.data.integration.isExecuting

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

/** Geographic position of an entity, resolved from its state attributes. */
@Immutable
data class EntityCoordinates(val latitude: Double, val longitude: Double)

/**
 * Everything needed to display an entity, fully resolved from the entity state and the
 * registries: the single source of truth for entity displays, so anything a display needs
 * should be resolved into this class at creation (see the [EntityDisplayItem] constructor
 * that takes a [Entity]).
 *
 * The item is a snapshot at resolution time.
 */
@Immutable
data class EntityDisplayItem(
    val entityId: String,
    val name: String,
    val icon: IIcon,
    val state: FriendlyState = FriendlyState.Literal(""),
    val rawState: String = "",
    /** Whether the entity is currently executing an action. */
    val isExecuting: Boolean = false,
    /** Whether the entity is in an active state, for state-colored rendering. */
    val isActive: Boolean = false,
    /** Geographic position of the entity, null when it has none. */
    val coordinates: EntityCoordinates? = null,
    val areaName: String? = null,
    val floorName: String? = null,
    val deviceName: String? = null,
    val isHidden: Boolean = false,
    val entityCategory: EntityCategory? = null,
    val displayPrecision: Int? = null,
    val labels: List<String> = emptyList(),
) {
    /**
     * Resolves the entity-derived fields from an [Entity], applying the [customIcon]
     * and [displayPrecision] when the caller has them, so [icon] and [state] are resolved
     * exactly once.
     */
    constructor(
        entity: Entity,
        name: String = entity.friendlyName,
        customIcon: IIcon? = null,
        areaName: String? = null,
        floorName: String? = null,
        deviceName: String? = null,
        isHidden: Boolean = false,
        entityCategory: EntityCategory? = null,
        displayPrecision: Int? = null,
        labels: List<String> = emptyList(),
    ) : this(
        entityId = entity.entityId,
        name = name,
        icon = customIcon ?: entity.getIcon(),
        state = entity.friendlyState(displayPrecision = displayPrecision),
        rawState = entity.state,
        isExecuting = entity.isExecuting(),
        isActive = entity.isActive(),
        coordinates = entity.coordinates(),
        areaName = areaName,
        floorName = floorName,
        deviceName = deviceName,
        isHidden = isHidden,
        entityCategory = entityCategory,
        displayPrecision = displayPrecision,
        labels = labels,
    )

    val domain: String get() = entityId.substringBefore('.')

    /**
     * Formatted subtitle combining area and device name, adapting the separator to the
     * layout direction. Null if the item has neither, or when it would just repeat [name].
     */
    fun subtitle(layoutDirection: LayoutDirection): String? = listOfNotNull(areaName, deviceName)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(if (layoutDirection == LayoutDirection.Ltr) " ▸ " else " ◂ ")
        ?.takeIf { it != name }

    companion object {

        private fun Entity.coordinates(): EntityCoordinates? {
            val latitude = (attributes["latitude"] as? Number)?.toDouble()
            val longitude = (attributes["longitude"] as? Number)?.toDouble()
            return if (latitude != null && longitude != null) EntityCoordinates(latitude, longitude) else null
        }
    }
}
