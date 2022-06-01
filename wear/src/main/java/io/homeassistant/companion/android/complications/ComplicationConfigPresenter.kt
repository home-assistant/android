package io.homeassistant.companion.android.complications

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import kotlinx.coroutines.flow.Flow

interface ComplicationConfigPresenter {
    suspend fun isConnected(): Boolean
    fun getWebSocketState(): WebSocketState?

    suspend fun getEntities(): List<Entity<*>>?
    suspend fun getEntityUpdates(): Flow<Entity<*>>?
}
