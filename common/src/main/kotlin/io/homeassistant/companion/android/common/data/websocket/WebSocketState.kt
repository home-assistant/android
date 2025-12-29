package io.homeassistant.companion.android.common.data.websocket

enum class WebSocketState {
    AUTHENTICATING,
    ACTIVE,
    CLOSED_AUTH,
    CLOSED_OTHER,

    /** Connection closed because the URL changed (e.g., switched networks). Reconnects immediately. */
    CLOSED_URL_CHANGE,
}
