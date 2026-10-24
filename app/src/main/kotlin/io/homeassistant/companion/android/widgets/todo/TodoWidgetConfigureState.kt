package io.homeassistant.companion.android.widgets.todo

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType

@Stable
internal data class TodoWidgetConfigureState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val entityDisplayState: EntityDisplayState<EntityDisplayWithContext> = EntityDisplayState.Loading,
    val selectedEntityId: String? = null,
    val showCompleted: Boolean = true,
    val selectedBackgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val textColorHex: String? = null,
    val dynamicColorAvailable: Boolean = false,
    val isUpdateWidget: Boolean = false,
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { it.key == selectedServerId }

    val selectedEntity: EntityDisplay? = selectedEntityId
        ?.let { (entityDisplayState as? EntityDisplayState.Loaded<*>)?.entity(it) }

    val showConfiguration = selectedEntityId != null

    val isActionEnabled = selectedEntityId != null

    @StringRes
    val actionButtonLabel = if (isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget

    /** Resets the state that only makes sense for the previously selected server. */
    fun changeServer(serverId: Int): TodoWidgetConfigureState = copy(
        selectedServerId = serverId,
        selectedEntityId = null,
        entityDisplayState = EntityDisplayState.Loading,
    )
}
