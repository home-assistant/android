package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class AreaRegistryUpdatedEvent(val action: String, val areaId: String)
