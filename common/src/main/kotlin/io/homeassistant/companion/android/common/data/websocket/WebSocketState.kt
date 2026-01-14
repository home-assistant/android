package io.homeassistant.companion.android.common.data.websocket

/**
 * Represents the state of a WebSocket connection
 */
sealed interface WebSocketState {

    data object Initial : WebSocketState
    data class Closed(val reason: Reason) : WebSocketState {
        enum class Reason {
            AUTH,
            CHANGED_URL,
            OTHER,
        }
    }

    data object Authenticating : WebSocketState

    data object Active : WebSocketState

    companion object {
        val ClosedAuth = Closed(Closed.Reason.AUTH)
        val ClosedUrlChange = Closed(Closed.Reason.CHANGED_URL)
        val ClosedOther = Closed(Closed.Reason.OTHER)
    }
}
