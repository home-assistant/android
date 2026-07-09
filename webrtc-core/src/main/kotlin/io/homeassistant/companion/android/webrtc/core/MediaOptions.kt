package io.homeassistant.companion.android.webrtc.core

/**
 * Which media a WebRTC session negotiates.
 *
 * The microphone track itself is only created when talk-back is used (see [TwoWayAudio]),
 * whatever the negotiated [audio] direction; a direction that cannot send makes the microphone
 * permanently [MicState.Unavailable].
 *
 * @property video receive the camera video. Disable for audio-only sessions, for example
 * answering a doorbell as an audio call.
 * @property audio direction of the audio negotiation, [AudioDirection.SEND_RECEIVE] by default
 */
data class MediaOptions(val video: Boolean = true, val audio: AudioDirection = AudioDirection.SEND_RECEIVE) {

    /**
     * Direction of the audio m-line, from the point of view of this client.
     */
    enum class AudioDirection {
        /** Play the camera audio and allow talk-back. */
        SEND_RECEIVE,

        /** Only play the camera audio; the microphone is never available. */
        RECEIVE_ONLY,

        /**
         * Only send microphone audio; nothing is received or played. For voice broadcast use
         * cases, like talking through a doorbell without listening to it.
         */
        SEND_ONLY,
    }
}
