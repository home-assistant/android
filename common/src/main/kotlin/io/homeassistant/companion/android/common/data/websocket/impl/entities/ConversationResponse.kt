package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class ConversationResponse(
    val response: ConversationSpeechResponse,
    val conversationId: String? = null,
    // Default value is set for backward compatibility.
    val continueConversation: Boolean = false,
)
