package io.homeassistant.companion.android.widgets.climate

import androidx.compose.runtime.Stable
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
// TODO climate: corregir gramatica:
/**
 * Complete UI state for the Climate widget configuration screen.
 *
 * [availableEntities] are the hvac that can still be added (the picker options, already
 * filtered to exclude the selection) and [selectedEntities] are the chosen hvac with their
 * resolved display information. Both are precomputed here so the UI never filters or resolves
 * names itself.
 */
@Stable
internal data class ClimateWidgetConfigureState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val selectedEntityId: String,
    val entityDisplayState: EntityDisplayState<EntityDisplayWithContext> = EntityDisplayState.Loading,
    val textColor: String = "",             // TODO climate: ajustar el default
    val selectedBackgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val dynamicColorAvailable: Boolean = false,
    val isUpdateWidget: Boolean = false,
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { it.key == selectedServerId }


    internal val EntityDisplayState<EntityDisplayWithContext>.entitiesDropdownItems: List<HADropdownItem<String>>
        get() = when (this) {
            is EntityDisplayState.Loaded -> entities.map {
                HADropdownItem(
                    key = it.entityId,
                    label = it.name,
                )
            }
            is EntityDisplayState.Loading -> emptyList()
            is EntityDisplayState.Error -> emptyList()
        }

    val isActionEnabled = selectedEntityId.isNotEmpty()

    fun changeServer(serverId: Int): ClimateWidgetConfigureState = copy(
        selectedServerId = serverId,
        selectedEntityId = "",
        entityDisplayState = EntityDisplayState.Loading,
    )
}
