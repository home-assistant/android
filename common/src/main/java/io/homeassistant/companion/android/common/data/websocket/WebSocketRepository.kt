package io.homeassistant.companion.android.common.data.websocket

interface WebSocketRepository {
    suspend fun sendPing(): Boolean
}
