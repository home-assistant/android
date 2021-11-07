package io.homeassistant.companion.android.common.data.websocket

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class SocketResponse(
    val id: Long?,
    val type: String,
    val success: Boolean?,
    val result: JsonNode?
)
