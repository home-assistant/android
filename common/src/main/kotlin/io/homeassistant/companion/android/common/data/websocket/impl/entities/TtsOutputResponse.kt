package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class TtsOutputResponse(
    val mediaId: String,
    val mimeType: String,
    val url: String
)
