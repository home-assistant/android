package io.homeassistant.companion.android.widgets.mediaplayer

import androidx.compose.runtime.Stable
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType

/**
 * Complete UI state for the Media Player Controls widget configuration screen.
 *
 * [availableEntities] are the media players that can still be added (the picker options, already
 * filtered to exclude the selection) and [selectedEntities] are the chosen players with their
 * resolved display information. Both are precomputed here so the UI never filters or resolves
 * names itself.
 */
@Stable
internal data class MediaPlayerControlsWidgetConfigureState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val selectedEntityIds: List<String> = emptyList(),
    val entityDisplayState: EntityDisplayState<EntityDisplayWithContext> = EntityDisplayState.Loading,
    val label: String = "",
    val showVolume: Boolean = true,
    val showSkip: Boolean = true,
    val showSeek: Boolean = true,
    val showSource: Boolean = true,
    val selectedBackgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val dynamicColorAvailable: Boolean = false,
    val isUpdateWidget: Boolean = false,
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { it.key == selectedServerId }

    val showConfiguration = selectedEntityIds.isNotEmpty()

    val selectedEntities = (entityDisplayState as? EntityDisplayState.Loaded)?.let { state ->
        selectedEntityIds.mapNotNull { state.entity(it) }
    } ?: emptyList()

    val availableEntities = if (entityDisplayState is EntityDisplayState.Loaded) {
        entityDisplayState.copy(entitiesById = entityDisplayState.entitiesById - selectedEntityIds.toSet())
    } else {
        entityDisplayState
    }

    val isActionEnabled = selectedEntities.isNotEmpty()

    fun changeServer(serverId: Int): MediaPlayerControlsWidgetConfigureState = copy(
        selectedServerId = serverId,
        selectedEntityIds = emptyList(),
        entityDisplayState = EntityDisplayState.Loading,
    )
}
