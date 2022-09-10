package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TemplateUpdatedEvent(
    val result: String?,
    val listeners: Map<String, Any>
)
