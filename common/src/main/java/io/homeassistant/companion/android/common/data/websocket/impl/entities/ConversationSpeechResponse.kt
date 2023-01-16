package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConversationSpeechResponse(
    val speech: ConversationSpeechPlainResponse,
    val card: Any?,
    val language: String?,
    val responseType: String?,
    val data: Map<String, Any?>?
)
