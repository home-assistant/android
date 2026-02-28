package io.homeassistant.companion.android.common.data.shortcuts.impl.entities

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server

data class ServerData(
    val entities: List<Entity> = emptyList(),
    val entityRegistry: List<EntityRegistryResponse> = emptyList(),
    val deviceRegistry: List<DeviceRegistryResponse> = emptyList(),
    val areaRegistry: List<AreaRegistryResponse> = emptyList(),
)

data class ServersData(val servers: List<Server>, val defaultServerId: Int)

data class ShortcutEditorData(val servers: List<Server>, val serverDataById: Map<Int, ServerData>)
