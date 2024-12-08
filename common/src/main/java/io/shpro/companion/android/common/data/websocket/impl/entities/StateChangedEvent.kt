package io.shpro.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.shpro.companion.android.common.data.integration.Entity

@JsonIgnoreProperties(ignoreUnknown = true)
data class StateChangedEvent(
    val entityId: String,
    val oldState: Entity<*>?,
    val newState: Entity<*>?
)
