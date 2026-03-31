package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class AssistPipelineResponse(
    val id: String,
    val language: String,
    val name: String,
    val conversationEngine: String,
    val conversationLanguage: String,
    val sttEngine: String? = null,
    val sttLanguage: String? = null,
    val ttsEngine: String? = null,
    val ttsLanguage: String? = null,
    val ttsVoice: String? = null,
)
