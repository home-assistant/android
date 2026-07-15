package io.homeassistant.companion.android.webrtc.core.signaling

import kotlinx.coroutines.flow.Flow

/**
 * Transport-agnostic client for the WebRTC signaling exchange.
 *
 * The Home Assistant implementation lives in the `:webrtc-signaling-ha` module; this interface
 * keeps the session engine independent from the transport so it can be unit tested with fakes and
 * reused with other backends.
 */
interface SignalingClient {

    /**
     * Get the stream types available for a camera entity.
     *
     * @throws SignalingException if the server could not be queried
     */
    suspend fun getStreamCapabilities(entityId: String): Set<StreamType>

    /**
     * Get the configuration (STUN/TURN servers, optional data channel) to create the peer
     * connection with.
     *
     * @throws SignalingException if the server rejected the request, for example when the camera
     * does not support WebRTC
     */
    suspend fun getClientConfig(entityId: String): RtcClientConfig

    /**
     * Send the SDP offer for a new session and observe the signaling events for it.
     *
     * The returned Flow is cold: collecting it starts the session on the server and cancelling
     * the collection closes the session. There is at most one [SignalingEvent.Session] and one
     * [SignalingEvent.Answer] per collection, while [SignalingEvent.Candidate] can be emitted at
     * any time (trickle ICE).
     *
     * @throws SignalingException from the collection if the session could not be started
     */
    fun openSession(entityId: String, offerSdp: String): Flow<SignalingEvent>

    /**
     * Send a local ICE candidate for a session opened with [openSession].
     *
     * @param sessionId the identifier received in [SignalingEvent.Session]
     * @return `true` if the server accepted the candidate. A rejected candidate is not fatal, the
     * connection can still be established through the other candidates.
     */
    suspend fun sendCandidate(entityId: String, sessionId: String, candidate: IceCandidateInit): Boolean
}

/**
 * Stream types a camera entity supports, mirroring the `StreamType` values of Home Assistant
 * Core's camera integration.
 */
enum class StreamType {
    HLS,
    WEB_RTC,
}

/**
 * Configuration for creating a peer connection, from the `RTCConfiguration` sent by the server.
 *
 * @property iceServers the STUN/TURN servers to gather candidates with
 * @property dataChannelLabel label of a data channel to open before sending the offer, used by
 * some WebRTC providers (like go2rtc). `null` when the provider does not use a data channel.
 */
data class RtcClientConfig(val iceServers: List<RtcIceServer> = emptyList(), val dataChannelLabel: String? = null)

/**
 * A single STUN or TURN server entry, mirroring the W3C `RTCIceServer` dictionary.
 */
data class RtcIceServer(val urls: List<String>, val username: String? = null, val credential: String? = null)

/**
 * An ICE candidate in either direction, mirroring the W3C `RTCIceCandidateInit` dictionary.
 */
data class IceCandidateInit(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val usernameFragment: String? = null,
)

/**
 * Event received during the signaling exchange of one session.
 */
sealed interface SignalingEvent {
    /** The server created the session; [sessionId] is needed to send local candidates. */
    data class Session(val sessionId: String) : SignalingEvent

    /** The SDP answer from the camera or its WebRTC provider. */
    data class Answer(val sdp: String) : SignalingEvent

    /** A remote ICE candidate (trickle ICE, can arrive before or after [Answer]). */
    data class Candidate(val candidate: IceCandidateInit) : SignalingEvent

    /** The negotiation failed and the session is unusable. */
    data class Error(val code: String?, val message: String?) : SignalingEvent
}

/**
 * Failure reported by the signaling backend.
 *
 * @property code machine readable error code from the server when available, for example
 * `webrtc_offer_failed` or `webrtc_get_client_config_failed`
 */
class SignalingException(val code: String? = null, message: String? = null, cause: Throwable? = null) :
    Exception(message, cause)
