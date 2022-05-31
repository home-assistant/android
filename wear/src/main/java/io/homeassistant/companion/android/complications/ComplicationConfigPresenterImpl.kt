package io.homeassistant.companion.android.complications

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.home.HomeView
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ComplicationConfigPresenterImpl @Inject constructor(
    private val integrationUseCase: IntegrationRepository,
    private val webSocketUseCase: WebSocketRepository
) : ComplicationConfigPresenter {
    override suspend fun isConnected(): Boolean {
        return integrationUseCase.isRegistered()
    }

    override fun getWebSocketState(): WebSocketState? {
        return webSocketUseCase.getConnectionState()
    }

    override suspend fun getEntities(): List<Entity<*>>? {
        return integrationUseCase.getEntities()
    }

    override suspend fun getEntityUpdates(): Flow<Entity<*>>? {
        return integrationUseCase.getEntityUpdates()
    }
}