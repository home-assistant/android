package io.homeassistant.companion.android.webrtc.core

/**
 * Which media a WebRTC session negotiates.
 *
 * The audio direction is always negotiated send-and-receive (the microphone track itself is only
 * created when talk-back is used, see [TwoWayAudio]); disabling [video] negotiates an audio-only
 * session with a single audio m-line, so nothing is decoded or rendered.
 *
 * @property video receive the camera video. Disable for voice-only sessions, for example
 * answering a doorbell as an audio call.
 */
data class MediaOptions(val video: Boolean = true)
