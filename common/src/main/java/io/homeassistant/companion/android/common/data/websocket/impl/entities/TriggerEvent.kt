package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.homeassistant.companion.android.common.data.integration.Entity

@JsonIgnoreProperties(ignoreUnknown = true)
data class TriggerEvent(
    val platform: String,
    val entityId: String?,
    val fromState: Entity<*>?,
    val toState: Entity<*>?
)
