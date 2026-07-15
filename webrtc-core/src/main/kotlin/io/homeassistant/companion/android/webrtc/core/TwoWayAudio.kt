package io.homeassistant.companion.android.webrtc.core

import kotlinx.coroutines.flow.StateFlow

/**
 * State of the microphone of a [TwoWayAudio] session.
 */
sealed interface MicState {
    /** The microphone is not capturing. */
    data object Off : MicState

    /** The microphone is live and audio is being sent to the camera. */
    data object Live : MicState

    /**
     * The microphone cannot be enabled right now, for example because the session is not active.
     * The intent is remembered and applied when the session (re)connects.
     */
    data object Unavailable : MicState
}

/**
 * Capability interface for talk-back (two-way audio), implemented by the WebRTC player.
 *
 * The `RECORD_AUDIO` runtime permission must be granted by the consumer before enabling the
 * microphone; this interface only controls the WebRTC audio track.
 */
interface TwoWayAudio {
    /** Current state of the microphone. */
    val micState: StateFlow<MicState>

    /**
     * Enable or disable sending microphone audio to the camera. Designed to be push-to-talk
     * friendly: the audio track is negotiated up front and toggling does not renegotiate the
     * session.
     */
    fun setMicEnabled(enabled: Boolean)
}
