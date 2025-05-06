package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// TODO convert this into a sealed class
@Serializable
data class SocketResponse(
    val id: Long? = null,
    val type: String,
    val success: Boolean? = null,
    val result: JsonElement? = null,
    val event: JsonElement? = null,
    val error: JsonElement? = null,
    val haVersion: String? = null
)
