package io.homeassistant.companion.android.settings.qs

import androidx.compose.runtime.Stable
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse

@Stable
internal data class ManageTilesState(
    val selectedTileId: String = "",
    val entities: List<Entity> = emptyList(),
    val entityRegistry: List<EntityRegistryResponse> = emptyList(),
    val deviceRegistry: List<DeviceRegistryResponse> = emptyList(),
    val areaRegistry: List<AreaRegistryResponse> = emptyList(),
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val selectedIconId: String? = null,
    val selectedIcon: IIcon? = null,
    val selectedEntityId: String = "",
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

    val showResetIcon = selectedIconId != null && selectedEntityId.isNotBlank()

    val submitEnabled = tileLabel.isNotBlank() &&
        serversDropdownItems.any { it.key == selectedServerId } &&
        entities.any { it.entityId == selectedEntityId }
}
