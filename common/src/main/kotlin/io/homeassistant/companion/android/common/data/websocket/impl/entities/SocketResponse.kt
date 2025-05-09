package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.MapAnySerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule

@Serializable
sealed interface SocketResponse {
    fun maybeId() = (this as? SocketResponseWithId)?.id

    companion object {
        // This module needs to be given to the Json builder to be able to deserialize unknown types
        // Without this if we receive a message with an unknown type we will get a runtime exception
        internal val socketResponseSerializerModuler = SerializersModule {
            polymorphicDefaultDeserializer(SocketResponse::class) {
                UnknownTypeSocketResponse.serializer()
            }
        }
    }
}

/**
 * This class is used as fallback when the type received is not known within the codebase
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = MapAnySerializer::class)
class UnknownTypeSocketResponse(raw: Map<String, @Polymorphic Any>) : SocketResponse, Map<String, Any?> by raw

@Serializable
sealed interface AuthSocketResponse : SocketResponse {
    val haVersion: String?
}

@Serializable
sealed interface SocketResponseWithId : SocketResponse {
    val id: Long?
}

@Serializable
sealed interface RawMessageSocketResponse : SocketResponseWithId {
    val success: Boolean?
    val result: JsonElement? // Todo Maybe a template?
    val error: JsonElement?
}

@Serializable
@SerialName("auth_required")
class AuthRequiredSocketResponse : SocketResponse

@Serializable
@SerialName("auth_invalid")
// TODO not sure haVersion is sent here
// Used by the parser
data class AuthInvalidSocketResponse(override val haVersion: String? = null) : AuthSocketResponse

@Serializable
@SerialName("auth_ok")
data class AuthOkSocketResponse(override val haVersion: String? = null) : AuthSocketResponse

@Serializable
@SerialName("result")
data class MessageSocketResponse(
    override val id: Long? = null,
    override val success: Boolean? = null,
    override val result: JsonElement? = null,
    override val error: JsonElement? = null,
) : RawMessageSocketResponse

@Serializable
@SerialName("pong")
data class PongSocketResponse(
    override val id: Long? = null,
    override val success: Boolean? = null,
    override val result: JsonElement? = null,
    override val error: JsonElement? = null,
) : RawMessageSocketResponse

@Serializable
@SerialName("event")
data class EventSocketResponse(
    override val id: Long? = null,
    val event: JsonElement? = null,
) : SocketResponseWithId
