package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.AnySerializer
import io.homeassistant.companion.android.common.util.MapAnySerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

@Serializable
data class ConversationSpeechResponse(
    val speech: ConversationSpeechPlainResponse? = null,
    @Serializable(with = AnySerializer::class)
    val card: Any? = null,
    val language: String? = null,
    val responseType: String? = null,
    @Serializable(with = MapAnySerializer::class)
    val data: Map<String, @Polymorphic Any?>? = null,
)
