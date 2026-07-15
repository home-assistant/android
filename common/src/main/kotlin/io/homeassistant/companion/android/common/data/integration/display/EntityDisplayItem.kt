package io.homeassistant.companion.android.common.data.integration.display

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.getIcon

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

/** Fully resolved display information for an entity. */
@Immutable
data class EntityDisplayItem(
    val entityId: String,
    val name: String,
    val icon: IIcon,
    val areaName: String? = null,
    val floorName: String? = null,
    val deviceName: String? = null,
    val isHidden: Boolean = false,
    val entityCategory: EntityCategory? = null,
    val displayPrecision: Int? = null,
    val labels: List<String> = emptyList(),
) {
    val domain: String get() = entityId.substringBefore('.')

    /**
     * Formatted subtitle combining area and device name, adapting the separator to the
     * layout direction. Null if the item has neither.
     */
    fun subtitle(layoutDirection: LayoutDirection): String? = listOfNotNull(areaName, deviceName)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(if (layoutDirection == LayoutDirection.Ltr) " ▸ " else " ◂ ")

    companion object {
        /**
         * Builds an item from an [Entity] alone, without any registry metadata (no
         * area/floor/device names). Meant for callers that cannot reach the websocket API,
         * such as the Wear favorites settings screen, and for previews.
         */
        fun from(entity: Entity, context: Context): EntityDisplayItem = EntityDisplayItem(
            entityId = entity.entityId,
            name = entity.friendlyName,
            icon = entity.getIcon(context),
        )
    }
}
