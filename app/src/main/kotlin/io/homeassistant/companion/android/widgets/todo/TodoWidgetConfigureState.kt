package io.homeassistant.companion.android.widgets.todo

import androidx.compose.runtime.Stable
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureState

@Stable
internal data class TodoWidgetConfigureState(
    override val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    override val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val entityDisplayState: EntityDisplayState<EntityDisplayWithContext> = EntityDisplayState.Loading,
    val selectedEntityId: String? = null,
    val showCompleted: Boolean = true,
    val selectedBackgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val textColorHex: String? = null,
    val dynamicColorAvailable: Boolean = false,
    override val isUpdateWidget: Boolean = false,
) : WidgetConfigureState {
    val selectedEntity: EntityDisplay? = selectedEntityId
        ?.let { (entityDisplayState as? EntityDisplayState.Loaded<*>)?.entity(it) }

    val showConfiguration = selectedEntityId != null

    override val isActionEnabled = selectedEntityId != null

    /** Resets the state that only makes sense for the previously selected server. */
    fun changeServer(serverId: Int): TodoWidgetConfigureState = copy(
        selectedServerId = serverId,
        selectedEntityId = null,
        entityDisplayState = EntityDisplayState.Loading,
    )
}
