package io.homeassistant.companion.android.webrtc.core

/**
 * State of the microphone of an [AudioSender].
 */
sealed interface MicState {
    /** The microphone is not capturing. */
    data object Off : MicState

    /** The microphone is live and audio is being sent to the peer. */
    data object Live : MicState

    /** The microphone cannot be enabled right now, for example because the session is not active. */
    data object Unavailable : MicState
}
