package io.homeassistant.companion.android.settings.qs

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.servers.ServerManager

/** A tile slot with the label of its configured tile, ready to be displayed in the slot picker. */
internal data class TileSlotItem(val id: TileId, @StringRes val nameRes: Int, val label: String?) {
    constructor(tileSlot: TileSlot, label: String? = null) : this(
        id = tileSlot.id,
        nameRes = tileSlot.nameRes,
        label = label,
    )
}

@Stable
internal data class ManageTilesState(
    val selectedTileId: TileId = tileSlots.first().id,
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val entityDisplayState: EntityDisplayState = EntityDisplayState.Loading,
    val customIcon: IIcon? = null,
    val selectedEntityId: String? = null,
    val tileLabel: String = "",
    val tileSubtitle: String = "",
    val submitButtonLabel: Int = commonR.string.tile_save,
    val selectedShouldVibrate: Boolean = false,
    val tileAuthRequired: Boolean = false,
    val showSubtitle: Boolean = false,
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
    val tileSlotItems: List<TileSlotItem> = tileSlots.map(::TileSlotItem),
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { server -> server.key == selectedServerId }

    /** Icon shown for the tile: the user-selected [customIcon], or the icon of the selected entity once loaded. */
    val selectedIcon = customIcon
        ?: selectedEntityId?.let { (entityDisplayState as? EntityDisplayState.Loaded)?.entity(it)?.icon }

    val showResetIcon = customIcon != null && !selectedEntityId.isNullOrBlank()

    val submitEnabled = tileLabel.isNotBlank() &&
        selectedEntityId != null &&
        serversDropdownItems.any { it.key == selectedServerId } &&
        (entityDisplayState as? EntityDisplayState.Loaded)?.entity(selectedEntityId) != null

    companion object {
        fun ManageTilesState.changeServer(serverId: Int): ManageTilesState =
            copy(selectedServerId = serverId, selectedEntityId = null, entityDisplayState = EntityDisplayState.Loading)
    }
}
