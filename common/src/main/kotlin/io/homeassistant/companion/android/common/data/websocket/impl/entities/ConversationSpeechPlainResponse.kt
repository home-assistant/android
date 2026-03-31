package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class ConversationSpeechPlainResponse(val plain: Map<String, String?>? = null)
