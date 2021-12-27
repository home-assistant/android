package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class AreaRegistryUpdatedEvent(
    val action: String,
    val areaId: String
)
