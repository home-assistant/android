package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class SocketResponse(
    val id: Long?,
    val type: String,
    val success: Boolean?,
    val result: JsonNode?,
    val event: JsonNode?,
    val error: JsonNode?,
    val haVersion: String?
)
