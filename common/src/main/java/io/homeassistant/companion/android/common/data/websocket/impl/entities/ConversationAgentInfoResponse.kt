package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConversationAgentInfoResponse(
    val attribution: ConversationAgentAttribution?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConversationAgentAttribution(
    val name: String,
    val url: String?
)
