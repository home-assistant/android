package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class EntityRegistryResponse(
    val areaId: String?,
    val configEntryId: String?,
    val deviceId: String?,
    val disabledBy: String?,
    val entityCategory: String?,
    val entityId: String,
    val icon: String?,
    val name: String?,
    val platform: String
)
