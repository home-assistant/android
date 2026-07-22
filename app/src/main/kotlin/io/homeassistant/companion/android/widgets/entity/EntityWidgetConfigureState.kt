package io.homeassistant.companion.android.widgets.entity

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction

@Stable
internal data class EntityWidgetConfigureState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val entityDisplayState: EntityDisplayState = EntityDisplayState.Loading,
    val selectedEntityId: String? = null,
    val availableAttributes: List<String> = emptyList(),
    val selectedAttributeIds: List<String> = emptyList(),
    val customAttribute: String = "",
    val label: String = "",
    val textSize: String = DEFAULT_TEXT_SIZE,
    val stateSeparator: String = "",
    val attributeSeparator: String = "",
    val selectedTapAction: WidgetTapAction = WidgetTapAction.REFRESH,
    val selectedBackgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val textColorHex: String? = null,
    val dynamicColorAvailable: Boolean = false,
    val isUpdateWidget: Boolean = false,
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { it.key == selectedServerId }

    val isToggleable = selectedEntityId?.substringBefore('.') in EntityExt.APP_PRESS_ACTION_DOMAINS

    @StringRes
    val textSizeError = commonR.string.widget_text_size_error
        .takeIf { textSize.toFloatOrNull()?.let { size -> size.isFinite() && size > 0 } != true }

    val unselectedAttributes = availableAttributes.filterNot(selectedAttributeIds::contains)

    val selectedEntity: EntityDisplayItem? = selectedEntityId
        ?.let { (entityDisplayState as? EntityDisplayState.Loaded)?.entity(it) }

    val showConfiguration = selectedEntityId != null

    val isActionEnabled = selectedEntityId != null && textSizeError == null

    @StringRes
    val actionButtonLabel = if (isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget

    val textSizeOrDefault: Float
        get() = textSize.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 } ?: DEFAULT_TEXT_SIZE.toFloat()

    /** Resets the state that only makes sense for the previously selected server. */
    fun changeServer(serverId: Int): EntityWidgetConfigureState = copy(
        selectedServerId = serverId,
        selectedEntityId = null,
        selectedAttributeIds = emptyList(),
        availableAttributes = emptyList(),
        selectedTapAction = WidgetTapAction.REFRESH,
        entityDisplayState = EntityDisplayState.Loading,
    )
}

internal const val DEFAULT_TEXT_SIZE = "30"
