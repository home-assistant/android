package io.homeassistant.companion.android.webrtc.core.session

import io.homeassistant.companion.android.webrtc.core.MediaOptions
import io.homeassistant.companion.android.webrtc.core.RtcStatsSnapshot
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
internal interface PeerConnectionController {

    /** Events from the peer connection. */
    val events: Flow<PeerConnectionEvent>

    /** Create the SDP offer and set it as local description, which starts ICE gathering. */
    suspend fun createOffer(): String

    /** Apply the SDP answer received from the server as remote description. */
    suspend fun setAnswer(sdp: String)

    /**
     * Add a remote ICE candidate. Must only be called after [setAnswer] succeeded.
     *
     * @return `true` if the candidate was accepted by the peer connection
     */
    suspend fun addRemoteCandidate(candidate: IceCandidateInit): Boolean

    /** Enable or disable the microphone track (created disabled). */
    suspend fun setMicrophoneEnabled(enabled: Boolean)

    /** Enable or disable the playback of the remote audio track. */
    suspend fun setRemoteAudioEnabled(enabled: Boolean)

    /** Attach a sink receiving the decoded remote video frames. */
    suspend fun addVideoSink(sink: VideoSink)

    /** Detach a sink previously attached with [addVideoSink]. */
    suspend fun removeVideoSink(sink: VideoSink)

    /**
     * Read a snapshot of the current transport and media statistics. The returned bitrates are
     * not filled: callers should derive them from the delta between consecutive snapshots.
     */
    suspend fun getStats(): RtcStatsSnapshot

    /**
     * Release all native resources (tracks, sources, peer connection). Idempotent; the controller
     * cannot be used afterwards.
     */
    suspend fun dispose()

    /** Creates one [PeerConnectionController]. */
    interface Factory {
        /**
         * @param config the server configuration (ICE servers, optional data channel)
         * @param mediaOptions the media directions to negotiate in the offer
         */
        suspend fun create(
            config: RtcClientConfig,
            mediaOptions: MediaOptions = MediaOptions(),
        ): PeerConnectionController
    }
}

/** Event emitted by a [PeerConnectionController]. */
internal sealed interface PeerConnectionEvent {
    /** A local ICE candidate was gathered and must be sent to the server. */
    data class LocalCandidate(val candidate: IceCandidateInit) : PeerConnectionEvent

    /** The overall connection state (ICE + DTLS) changed. */
    data class ConnectionStateChanged(val state: RtcConnectionState) : PeerConnectionEvent
}

/**
 * Connection state of the peer connection, mirroring the W3C `RTCPeerConnectionState`.
 */
internal enum class RtcConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    CLOSED,
}
