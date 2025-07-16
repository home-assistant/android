package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class AreaRegistryResponse(val areaId: String, val name: String, val picture: String? = null)
