package io.homeassistant.companion.android.webrtc.core.session

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.signaling.IceCandidateInit
import io.homeassistant.companion.android.webrtc.core.signaling.RtcClientConfig
import kotlinx.coroutines.flow.Flow
import livekit.org.webrtc.VideoSink

/**
 * Thin facade over one libwebrtc `PeerConnection`, so [WebRtcSession] stays pure Kotlin and can
 * be unit tested without native code.
 *
 * A controller wraps exactly one peer connection: it is created for one negotiation and disposed
 * when the session ends or reconnects. Implementations must be safe to call from any thread.
 */
interface PeerConnectionController {

    /**
     * Events from the peer connection. Events emitted before the Flow is collected are buffered,
     * so no local candidate is lost between [createOffer] and the collection starting.
     */
    val events: Flow<PeerConnectionEvent>

    /**
     * Create the SDP offer (video receive-only when negotiated, audio send-and-receive with the
     * microphone off) and set it as local description, which starts ICE gathering.
     *
     * @return the SDP of the offer to send to the server
     */
    suspend fun createOffer(): String

    /**
     * Apply the SDP answer received from the server as remote description.
     */
    suspend fun setAnswer(sdp: String)

    /**
     * Whether the remote end accepts microphone audio. Only meaningful once [setAnswer] was
     * applied (the answer carries the negotiated audio direction); `true` before that.
     */
    val isMicrophoneSupported: Boolean

    /**
     * Snapshot of the standardized WebRTC statistics of this connection, for debugging.
     *
     * @return the snapshot, or `null` when the controller is disposed or stats are unavailable
     */
    suspend fun getStats(): RtcDebugStats?

    /**
     * Add a remote ICE candidate. Must only be called after [setAnswer] succeeded.
     *
     * @return `true` if the candidate was accepted by the peer connection
     */
    fun addRemoteCandidate(candidate: IceCandidateInit): Boolean

    /** Enable or disable the microphone track (created disabled). */
    fun setMicrophoneEnabled(enabled: Boolean)

    /** Enable or disable the playback of the remote audio track. */
    fun setRemoteAudioEnabled(enabled: Boolean)

    /** Attach a sink receiving the decoded remote video frames. */
    fun addVideoSink(sink: VideoSink)

    /** Detach a sink previously attached with [addVideoSink]. */
    fun removeVideoSink(sink: VideoSink)

    /**
     * Release all native resources (tracks, sources, peer connection). Idempotent; the controller
     * cannot be used afterwards.
     */
    fun dispose()

    /**
     * Creates one [PeerConnectionController] per negotiation.
     */
    fun interface Factory {
        fun create(config: RtcClientConfig, mediaOptions: MediaOptions): PeerConnectionController
    }
}

/**
 * Event emitted by a [PeerConnectionController].
 */
sealed interface PeerConnectionEvent {
    /** A local ICE candidate was gathered and must be sent to the server. */
    data class LocalCandidate(val candidate: IceCandidateInit) : PeerConnectionEvent

    /** The overall connection state (ICE + DTLS) changed. */
    data class ConnectionStateChanged(val state: RtcConnectionState) : PeerConnectionEvent
}

/**
 * Connection state of the peer connection, mirroring the W3C `RTCPeerConnectionState`.
 */
enum class RtcConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    CLOSED,
}
