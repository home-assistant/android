package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class EntityRegistryResponse(
    val areaId: String?,
    val deviceId: String?,
    val entityCategory: String?,
    val entityId: String,
    val hiddenBy: String?
)
