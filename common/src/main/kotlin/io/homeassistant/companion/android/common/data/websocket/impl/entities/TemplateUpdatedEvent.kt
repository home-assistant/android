package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.MapAnySerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
data class TemplateUpdatedEvent(
    val result: String? = null,
    @Serializable(with = MapAnySerializer::class)
    val listeners: Map<String, @Polymorphic Any?>,
)
