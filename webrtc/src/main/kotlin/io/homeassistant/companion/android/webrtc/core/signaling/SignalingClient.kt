package io.homeassistant.companion.android.webrtc.core.signaling

import kotlinx.coroutines.flow.Flow

/**
 * Opaque identifier the signaling server assigns to a session. The library passes it around as a
 * token; only the [SignalingClient] creates one (from the server) and reads its [value] (to talk
 * back to the server), so the rest of the module never handles the raw string.
 */
@JvmInline
value class WebRtcSessionId(val value: String)

/**
 * Transport-agnostic client for the WebRTC signaling exchange of a single session. An instance is
 * bound to the media source it signals for.
 */
interface SignalingClient {

    /**
     * Get the configuration (STUN/TURN servers, optional data channel) to create the peer
     * connection with. Fails, for example, when the source does not support WebRTC.
     */
    suspend fun getClientConfig(): SignalingResult<RtcClientConfig>

    /**
     * Send the SDP offer for a new session and observe the signaling events for it.
     *
     * The returned Flow is cold: collecting it starts the session on the server and cancelling
     * the collection closes the session. It completes when the server ends the session or the
     * signaling transport is lost; the session is never reopened on the collector's behalf. There
     * is at most one [SignalingEvent.Session] and one [SignalingEvent.Answer] per collection,
     * while [SignalingEvent.Candidate] can be emitted at any time (trickle ICE). A session that
     * cannot be started, or that the server rejects, is reported as a terminal
     * [SignalingEvent.Error].
     */
    fun openSession(offerSdp: String): Flow<SignalingEvent>

    /**
     * Send a local ICE candidate for a session opened with [openSession].
     *
     * @param sessionId the identifier received in [SignalingEvent.Session]
     * @return `true` if the server accepted the candidate. A rejected candidate is not fatal, the
     * connection can still be established through the other candidates.
     */
    suspend fun sendCandidate(sessionId: WebRtcSessionId, candidate: IceCandidateInit): Boolean
}

/** Outcome of a signaling request. */
sealed interface SignalingResult<out T> {
    /** The request succeeded with [value]. */
    data class Success<T>(val value: T) : SignalingResult<T>

    /**
     * The request failed.
     *
     * @property code machine readable error code from the server when available, for example
     * `webrtc_offer_failed` or `webrtc_get_client_config_failed`
     */
    data class Failure(val code: String? = null, val message: String? = null) : SignalingResult<Nothing>
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

/** An ICE candidate in either direction, mirroring the W3C `RTCIceCandidateInit` dictionary. */
data class IceCandidateInit(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val usernameFragment: String? = null,
)

/** Event received during the signaling exchange of one session. */
sealed interface SignalingEvent {
    /** The server created the session; [sessionId] is needed to send local candidates. */
    data class Session(val sessionId: WebRtcSessionId) : SignalingEvent

    /** The SDP answer from the remote peer or its WebRTC provider. */
    data class Answer(val sdp: String) : SignalingEvent

    /** A remote ICE candidate (trickle ICE, can arrive before or after [Answer]). */
    data class Candidate(val candidate: IceCandidateInit) : SignalingEvent

    /**
     * The session failed and is unusable, for example it could not be started or the server
     * rejected the negotiation. Terminal: no further events follow.
     */
    data class Error(val code: String?, val message: String?) : SignalingEvent
}
