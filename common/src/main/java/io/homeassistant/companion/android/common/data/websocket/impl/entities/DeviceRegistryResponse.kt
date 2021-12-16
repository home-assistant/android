package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class DeviceRegistryResponse(
    val id: String,
    val name: String,
    val areaId: String?
)
