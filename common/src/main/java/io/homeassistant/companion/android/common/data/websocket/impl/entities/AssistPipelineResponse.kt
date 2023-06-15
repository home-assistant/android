package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistPipelineResponse(
    val id: String,
    val language: String,
    val name: String,
    val conversationEngine: String,
    val conversationLanguage: String,
    val sttEngine: String?,
    val sttLanguage: String?,
    val ttsEngine: String?,
    val ttsLanguage: String?,
    val ttsVoice: String?
)
