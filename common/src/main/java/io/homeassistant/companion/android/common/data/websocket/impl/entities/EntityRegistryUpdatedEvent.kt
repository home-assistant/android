package io.homeassistant.companion.android.common.data.websocket.impl.entities

data class EntityRegistryUpdatedEvent(
    val action: String,
    val entityId: String,
    val changes: List<Map<String, String>>?,
    val oldEntityId: String?
)
