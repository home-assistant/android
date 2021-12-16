package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class EntityRegistryResponse(
    val entityId: String,
    val deviceId: String?,
    val areaId: String?
)
