package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistryResponse(
    val areaId: String?,
    val id: String
)
