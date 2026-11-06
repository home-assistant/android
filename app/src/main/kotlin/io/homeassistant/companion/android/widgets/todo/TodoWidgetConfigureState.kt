package io.homeassistant.companion.android.widgets.todo

import androidx.compose.runtime.Stable
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.configure.WidgetConfigureState
import io.homeassistant.companion.android.widgets.configure.defaultWidgetBackgroundType
import io.homeassistant.companion.android.widgets.configure.dynamicColorAvailable

@Stable
internal data class TodoWidgetConfigureState(
    override val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    override val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val entityDisplayState: EntityDisplayState<EntityDisplayWithContext> = EntityDisplayState.Loading,
    val selectedEntityId: String? = null,
    val showCompleted: Boolean = true,
    val selectedBackgroundType: WidgetBackgroundType = defaultWidgetBackgroundType(),
    val textColorHex: String? = null,
    val dynamicColorAvailable: Boolean = dynamicColorAvailable(),
    override val isUpdateWidget: Boolean = false,
) : WidgetConfigureState<TodoWidgetConfigureState> {
    val selectedEntity: EntityDisplay? = selectedEntityId
        ?.let { (entityDisplayState as? EntityDisplayState.Loaded<*>)?.entity(it) }

    val showConfiguration = selectedEntityId != null

    override val isActionEnabled = selectedEntityId != null

    override fun withServersDropdownItems(items: List<HADropdownItem<Int>>) = copy(serversDropdownItems = items)

    override fun withSelectedServerId(serverId: Int) = copy(selectedServerId = serverId)

    override fun withBackgroundType(backgroundType: WidgetBackgroundType) =
        copy(selectedBackgroundType = backgroundType)

    /** Resets the state that only makes sense for the previously selected server. */
    override fun changeServer(serverId: Int): TodoWidgetConfigureState = copy(
        selectedServerId = serverId,
        selectedEntityId = null,
        entityDisplayState = EntityDisplayState.Loading,
    )
}
