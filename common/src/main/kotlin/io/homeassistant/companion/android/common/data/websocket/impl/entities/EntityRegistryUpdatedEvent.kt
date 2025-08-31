package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.MapAnySerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
data class EntityRegistryUpdatedEvent(
    val action: String,
    val entityId: String,
    @Serializable(with = MapAnySerializer::class)
    val changes: Map<String, @Polymorphic Any?>? = null,
    val oldEntityId: String? = null,
)
