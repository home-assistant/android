package io.homeassistant.companion.android.webrtc.core

/** State of a [WebRtcSession]'s peer connection. */
sealed interface ConnectionState {
    /** The session has not been started, or has been stopped. */
    data object Idle : ConnectionState

    /** The session is negotiating with the server. */
    data object Connecting : ConnectionState

    /**
     * The connection is not up and is being (re)established: an earlier attempt has not connected
     * yet and is being retried, or an established stream dropped and is recovering.
     */
    data object Reconnecting : ConnectionState

    /** The peer connection is established and media is flowing. */
    data object Connected : ConnectionState

    /** The session ended with an unrecoverable error. */
    data class Failed(val failure: SessionFailure) : ConnectionState
}

/**
 * Reason a [WebRtcSession] ended in [ConnectionState.Failed].
 */
sealed interface SessionFailure {
    /**
     * The server rejected the session, for example because the stream does not support WebRTC
     * (`webrtc_offer_failed`, `webrtc_get_client_config_failed`).
     */
    data class Signaling(val code: String?, val message: String?) : SessionFailure

    /** The media connection could not be established, or was lost and could not be recovered. */
    data object ConnectionLost : SessionFailure

    /** An unexpected local error, for example the peer connection could not be created. */
    data class Internal(val message: String?) : SessionFailure
}
