package io.homeassistant.companion.android.settings.qs

import androidx.compose.runtime.Stable
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.servers.ServerManager

@Stable
internal data class ManageTilesState(
    val selectedTileId: String = "",
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val entityDisplayState: EntityDisplayState = EntityDisplayState.Loading,
    val selectedIconId: String? = null,
    val selectedIcon: IIcon? = null,
    val selectedEntityId: String? = null,
    val tileLabel: String = "",
    val tileSubtitle: String = "",
    val submitButtonLabel: Int = commonR.string.tile_save,
    val selectedShouldVibrate: Boolean = false,
    val tileAuthRequired: Boolean = false,
    val showSubtitle: Boolean = false,
    val tileSlotsDropdownItems: List<HADropdownItem<String>> = emptyList(),
    val serversDropdownItems: List<HADropdownItem<Int>> = emptyList(),
) {
    val showServerSelector = serversDropdownItems.size > 1 ||
        serversDropdownItems.none { server -> server.key == selectedServerId }

    val showResetIcon = selectedIconId != null && !selectedEntityId.isNullOrBlank()

    val submitEnabled = tileLabel.isNotBlank() &&
        selectedEntityId != null &&
        serversDropdownItems.any { it.key == selectedServerId } &&
        (entityDisplayState as? EntityDisplayState.Loaded)?.entities?.any { it.entityId == selectedEntityId } == true
}
