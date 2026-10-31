package io.homeassistant.companion.android.frontend.externalbus.outgoing

/**
 * The reason the app rejects a `webrtc/stream/start` or ends a stream with `webrtc/stream/stopped`,
 * mirroring the frontend's `WEBRTC_STREAM_ERROR_CODES` union in `external_messaging.ts`.
 *
 * The frontend understands a fixed set of codes, so the app sends one of the known subtypes; an
 * unexpected failure uses [Unknown], the frontend's generic code.
 */
sealed interface WebRtcStreamErrorCode {

    /** The value sent on the wire. */
    val value: String

    /** The user denied the microphone permission. */
    data object MicrophoneDenied : WebRtcStreamErrorCode {
        override val value = "microphone_denied"
    }

    /** No API for the server, or its connection never became ready. */
    data object ServerUnavailable : WebRtcStreamErrorCode {
        override val value = "server_unavailable"
    }

    /** `camera/webrtc/offer` failed, or Home Assistant sent an error event. */
    data object SignalingFailed : WebRtcStreamErrorCode {
        override val value = "signaling_failed"
    }

    /** The WebRTC connection failed, or dropped for more than the grace period. */
    data object ConnectionFailed : WebRtcStreamErrorCode {
        override val value = "connection_failed"
    }

    /** The session was not connected within the connection timeout after the permission step. */
    data object Timeout : WebRtcStreamErrorCode {
        override val value = "timeout"
    }

    /** The app stopped the stream itself: backgrounded, replaced by a new start, or a page load. */
    data object Interrupted : WebRtcStreamErrorCode {
        override val value = "interrupted"
    }

    /** The app does not know the requested stream type. */
    data object UnsupportedStreamType : WebRtcStreamErrorCode {
        override val value = "unsupported_stream_type"
    }

    /** An unexpected error; the accompanying message holds the detail. */
    data object Unknown : WebRtcStreamErrorCode {
        override val value = "unknown"
    }
}
