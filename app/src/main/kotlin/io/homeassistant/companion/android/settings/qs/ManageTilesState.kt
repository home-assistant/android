package io.homeassistant.companion.android.settings.qs

import android.os.Build
import androidx.compose.runtime.Stable
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.server.Server

@Stable
internal data class ManageTilesState(
    val tileSlots: List<TileSlot>,
    val selectedTileId: String = "",
    val servers: List<Server> = emptyList(),
    val sortedEntities: List<Entity> = emptyList(),
    val entityRegistry: List<EntityRegistryResponse> = emptyList(),
    val deviceRegistry: List<DeviceRegistryResponse> = emptyList(),
    val areaRegistry: List<AreaRegistryResponse> = emptyList(),
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val selectedIconId: String? = null,
    val selectedIcon: IIcon? = null,
    val selectedEntityId: String = "",
    val tileLabel: String = "",
    val tileSubtitle: String? = null,
    val submitButtonLabel: Int = commonR.string.tile_save,
    val selectedShouldVibrate: Boolean = false,
    val tileAuthRequired: Boolean = false,
    val tileSlotsDropdownItems: List<HADropdownItem<String>> = tileSlots.map {
        HADropdownItem(key = it.id, label = it.name)
    },
    val serversDropdownItems: List<HADropdownItem<Int>> = servers.map {
        HADropdownItem(key = it.id, label = it.friendlyName)
    },
) {
    val showSubtitle = SdkVersion.isAtLeast(Build.VERSION_CODES.Q)

    val showServerSelector = servers.size > 1 ||
        servers.none { server -> server.id == selectedServerId }

    val showResetIcon = selectedIconId != null && selectedEntityId.isNotBlank()

    val submitEnabled = tileLabel.isNotBlank() &&
        selectedServerId in servers.map { it.id } &&
        selectedEntityId in sortedEntities.map { it.entityId }
}

sealed interface TileInfoSnackbarEvent {
    data object DataMissing : TileInfoSnackbarEvent
    data object Added : TileInfoSnackbarEvent
    data object Updated : TileInfoSnackbarEvent
}
