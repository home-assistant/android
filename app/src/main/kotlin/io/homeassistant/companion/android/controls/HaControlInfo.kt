package io.homeassistant.companion.android.controls

import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse

data class HaControlInfo(
    val systemId: String,
    val entityId: String,
    val serverId: Int,
    val serverName: String? = null,
    val area: AreaRegistryResponse? = null,
    val authRequired: Boolean = false,
    val baseUrl: String? = null,
    val splitMultiServerIntoStructure: Boolean = false,
)
