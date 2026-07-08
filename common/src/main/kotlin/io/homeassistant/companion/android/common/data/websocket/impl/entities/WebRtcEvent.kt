package io.homeassistant.companion.android.common.data.websocket.impl.entities

import io.homeassistant.companion.android.common.util.UnknownJsonContent
import io.homeassistant.companion.android.common.util.UnknownJsonContentBuilder
import io.homeassistant.companion.android.common.util.UnknownJsonContentDeserializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule

/**
 * JSON mapper dedicated to the `camera/webrtc` WebSocket commands.
 *
 * These payloads cannot be handled by the shared [io.homeassistant.companion.android.common.util.kotlinJsonMapper]:
 * its global snake_case naming strategy would also rewrite explicit `@SerialName` values, while
 * the WebRTC API mixes snake_case keys (`session_id`) with the camelCase keys of the standard
 * `RTCIceCandidateInit`/`RTCConfiguration` dictionaries (`sdpMid`, `iceServers`, ...).
 */
internal val webRtcJsonMapper = Json {
    ignoreUnknownKeys = true
    // Omit null values so optional candidate fields are sent the same way as the frontend,
    // which leaves undefined values out of the JSON payload
    explicitNulls = false
    serializersModule = SerializersModule {
        polymorphicDefaultDeserializer(WebRtcEvent::class) { className ->
            object : UnknownJsonContentDeserializer<WebRtcEvent.Unknown>() {
                override val builder = UnknownJsonContentBuilder { content ->
                    WebRtcEvent.Unknown(className, content)
                }
            }
        }
    }
}

/**
 * Event received on a `camera/webrtc/offer` subscription.
 *
 * The server pushes these events while a WebRTC session is being negotiated: first a [Session]
 * with the identifier needed to send candidates back, then an [Answer], then zero or more
 * [Candidate]s (trickle ICE). An [Error] can arrive at any time and ends the negotiation.
 */
@Serializable
sealed interface WebRtcEvent {

    /** The server created a session and assigned it an identifier. */
    @Serializable
    @SerialName("session")
    data class Session(@SerialName("session_id") val sessionId: String) : WebRtcEvent

    /** The SDP answer from the camera or its WebRTC provider. */
    @Serializable
    @SerialName("answer")
    data class Answer(val answer: String) : WebRtcEvent

    /** A remote ICE candidate discovered by the camera or its WebRTC provider. */
    @Serializable
    @SerialName("candidate")
    data class Candidate(val candidate: WebRtcCandidate) : WebRtcEvent

    /** Negotiation failed, for example `webrtc_offer_failed`. */
    @Serializable
    @SerialName("error")
    data class Error(val code: String, val message: String? = null) : WebRtcEvent

    /**
     * Fallback for event types this version of the app does not know, so that a server-side
     * addition never breaks an ongoing subscription.
     */
    data class Unknown(override val discriminator: String?, override val content: JsonElement) :
        WebRtcEvent,
        UnknownJsonContent
}

/**
 * A standard `RTCIceCandidateInit` dictionary, exchanged in both directions during trickle ICE.
 *
 * The keys are camelCase on the wire (like in the W3C WebRTC specification), which is why this
 * class must be serialized with [webRtcJsonMapper] and not the shared snake_case mapper.
 */
@Serializable
data class WebRtcCandidate(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val usernameFragment: String? = null,
)
