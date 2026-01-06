package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class TtsOutputResponse(val mimeType: String, val url: String)
