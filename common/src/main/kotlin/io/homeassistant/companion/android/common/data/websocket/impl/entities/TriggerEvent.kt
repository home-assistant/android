package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.data.integration.Entity
import kotlinx.serialization.Serializable

@Serializable
data class TriggerEvent(
    val platform: String,
    val entityId: String? = null,
    val fromState: Entity? = null,
    val toState: Entity? = null,
)
