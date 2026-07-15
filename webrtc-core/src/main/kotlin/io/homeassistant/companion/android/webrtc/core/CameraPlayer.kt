package io.homeassistant.companion.android.webrtc.core

import kotlinx.coroutines.flow.StateFlow
import livekit.org.webrtc.VideoSink

/**
 * State of a [CameraPlayer].
 */
sealed interface PlayerState {
    /** The player has not been started, or has been stopped. */
    data object Idle : PlayerState

    /** The player is negotiating a new session with the server. */
    data object Connecting : PlayerState

    /** A session is established but media is not flowing (yet or anymore). */
    data object Buffering : PlayerState

    /** Media is flowing. */
    data object Playing : PlayerState

    /** The session ended with an unrecoverable error. Consumers may fall back to HLS. */
    data class Failed(val failure: PlayerFailure) : PlayerState
}

/**
 * Reason a [CameraPlayer] ended in [PlayerState.Failed].
 */
sealed interface PlayerFailure {
    /**
     * The server rejected the session, for example because the camera does not support WebRTC
     * (`webrtc_offer_failed`, `webrtc_get_client_config_failed`).
     */
    data class Signaling(val code: String?, val message: String?) : PlayerFailure

    /** The media connection could not be established, or was lost and could not be recovered. */
    data object ConnectionLost : PlayerFailure

    /** An unexpected local error, for example the peer connection could not be created. */
    data class Internal(val message: String?) : PlayerFailure
}

/**
 * A player rendering a live camera stream to one or more [VideoSink]s.
 *
 * This is the seam shared between the WebRTC implementation and (later) an ExoPlayer/HLS adapter,
 * so consumers can fall back from WebRTC to HLS the same way the frontend degrades
 * webrtc → mse → hls.
 *
 * All functions are safe to call from any thread.
 */
interface CameraPlayer {
    /** Current state of the player. */
    val state: StateFlow<PlayerState>

    /**
     * Attach a sink that will receive the decoded video frames. Can be called at any time,
     * including before [start]; the sink starts receiving frames as soon as the remote video
     * track is available.
     */
    fun attachVideoSink(sink: VideoSink)

    /** Detach a sink previously attached with [attachVideoSink]. */
    fun detachVideoSink(sink: VideoSink)

    /**
     * Enable or disable the playback of the remote audio (the camera microphone). Enabled by
     * default. This only mutes locally, the audio track keeps being received.
     */
    fun setAudioEnabled(enabled: Boolean)

    /** Start the player. Does nothing if it is already started or [release] has been called. */
    fun start()

    /**
     * Stop the player and end the session on the server. The player can be started again with
     * [start], which negotiates a new session.
     */
    fun stop()

    /** Stop the player and release all resources. The player cannot be used afterwards. */
    fun release()
}
