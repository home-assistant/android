package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.UnknownJsonContent
import io.homeassistant.companion.android.common.util.UnknownJsonContentBuilder
import io.homeassistant.companion.android.common.util.UnknownJsonContentDeserializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule

@Serializable
internal sealed interface SocketResponse {
    fun maybeId() = (this as? SocketResponseWithId)?.id

    companion object {
        /**
         * This module needs to be given to the Json builder to be able to deserialize unknown types.
         * Without this module, a runtime exception will be thrown for received messages with an unknown type.
         */
        internal val socketResponseSerializerModuler = SerializersModule {
            polymorphicDefaultDeserializer(SocketResponse::class) {
                object : UnknownJsonContentDeserializer<UnknownTypeSocketResponse>() {
                    override val builder = UnknownJsonContentBuilder { content ->
                        UnknownTypeSocketResponse(content)
                    }
                }
            }
        }
    }
}

/**
 * This class is used as fallback when the type received is not known within the codebase.
 * The type can be found directly within the [content].
 */
internal data class UnknownTypeSocketResponse(override val content: JsonElement) :
    SocketResponse,
    UnknownJsonContent

@Serializable
internal sealed interface AuthSocketResponse : SocketResponse {
    val haVersion: String?
}

@Serializable
internal sealed interface SocketResponseWithId : SocketResponse {
    val id: Long?
}

@Serializable
internal sealed interface RawMessageSocketResponse : SocketResponseWithId {
    val success: Boolean?
    val result: JsonElement?
    val error: JsonElement?
}

@Serializable
@SerialName("auth_required")
internal class AuthRequiredSocketResponse : SocketResponse

@Serializable
@SerialName("auth_invalid")
internal data class AuthInvalidSocketResponse(override val haVersion: String? = null) : AuthSocketResponse

@Serializable
@SerialName("auth_ok")
internal data class AuthOkSocketResponse(override val haVersion: String? = null) : AuthSocketResponse

@Serializable
@SerialName("result")
internal data class MessageSocketResponse(
    override val id: Long? = null,
    override val success: Boolean? = null,
    override val result: JsonElement? = null,
    override val error: JsonElement? = null,
) : RawMessageSocketResponse

@Serializable
@SerialName("pong")
internal data class PongSocketResponse(
    override val id: Long? = null,
    override val success: Boolean? = null,
    override val result: JsonElement? = null,
    override val error: JsonElement? = null,
) : RawMessageSocketResponse

@Serializable
@SerialName("event")
internal data class EventSocketResponse(override val id: Long? = null, val event: JsonElement? = null) :
    SocketResponseWithId
