package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConversationResponse(
    val response: ConversationSpeechResponse,
    val conversationId: String?,
    // Default value is set for backward compatibility.
    val continueConversation: Boolean = false
)
