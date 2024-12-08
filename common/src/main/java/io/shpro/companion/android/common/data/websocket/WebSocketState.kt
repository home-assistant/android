package io.shpro.companion.android.common.data.websocket

enum class WebSocketState {
    AUTHENTICATING,
    ACTIVE,
    CLOSED_AUTH,
    CLOSED_OTHER
}
