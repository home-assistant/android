package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class AreaRegistryResponse(
    val areaId: String,
    val name: String,
    val picture: String? = null,
    /** Only sent by Home Assistant 2024.3 or later. */
    val floorId: String? = null,
)
