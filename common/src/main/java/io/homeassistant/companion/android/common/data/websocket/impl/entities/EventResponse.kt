package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class EventResponse(
    val eventType: String,
    val timeFired: String,
    val data: StateChangedEvent
)
