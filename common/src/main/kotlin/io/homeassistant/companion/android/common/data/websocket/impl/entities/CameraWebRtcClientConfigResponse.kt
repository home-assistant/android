package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * Response for the `camera/webrtc/get_client_config` WebSocket command.
 *
 * The keys are camelCase on the wire (they mirror the W3C `RTCConfiguration` dictionary), so this
 * class must be deserialized with [webRtcJsonMapper] and not the shared snake_case mapper.
 *
 * @property configuration the `RTCConfiguration` to create the peer connection with
 * @property dataChannel label of a data channel the client should open, used by some WebRTC
 * providers (like go2rtc) to negotiate additional features. `null` when the provider does not use
 * a data channel.
 */
@Serializable
data class CameraWebRtcClientConfigResponse(
    val configuration: WebRtcConfiguration = WebRtcConfiguration(),
    val dataChannel: String? = null,
)

/**
 * The subset of the W3C `RTCConfiguration` dictionary sent by Home Assistant Core.
 */
@Serializable
data class WebRtcConfiguration(val iceServers: List<WebRtcIceServer> = emptyList())

/**
 * A single `RTCIceServer` entry (STUN or TURN server) of an `RTCConfiguration`.
 */
@Serializable
data class WebRtcIceServer(
    @Serializable(with = StringOrStringListSerializer::class)
    val urls: List<String> = emptyList(),
    val username: String? = null,
    val credential: String? = null,
)

/**
 * The `urls` member of `RTCIceServer` is allowed to be either a single string or a list of
 * strings. This serializer normalizes both shapes to a list.
 */
private object StringOrStringListSerializer :
    JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        element as? JsonArray ?: JsonArray(listOf(element))
}
