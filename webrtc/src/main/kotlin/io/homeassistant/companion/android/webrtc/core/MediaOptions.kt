package io.homeassistant.companion.android.webrtc.core

/**
 * Media directions to negotiate for a WebRTC session, chosen before the session is opened.
 *
 * @property videoIn negotiate a receive-only video transceiver (incoming video)
 * @property audioIn receive the remote audio (incoming audio)
 * @property audioOut capture the local microphone and send it to the peer (talk-back). The
 * `RECORD_AUDIO` runtime permission must be granted by the consumer before enabling this.
 */
data class MediaOptions(val videoIn: Boolean = true, val audioIn: Boolean = true, val audioOut: Boolean = false) {
    /** At least one direction must be negotiated for the session to be meaningful. */
    val hasAnyStream: Boolean get() = videoIn || audioIn || audioOut
}
