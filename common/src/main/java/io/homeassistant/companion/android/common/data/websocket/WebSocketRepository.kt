package io.homeassistant.companion.android.common.data.websocket

import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.ServiceCallRequest
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DomainResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

interface WebSocketRepository {
    suspend fun sendPing(): Boolean
    suspend fun getConfig(): GetConfigResponse?
    suspend fun getStates(): List<EntityResponse<Any>>
    suspend fun getServices(): List<DomainResponse>
    suspend fun getPanels(): List<String>
    suspend fun callService(request: ServiceCallRequest)
    @ExperimentalCoroutinesApi
    suspend fun getStateChanges(): Flow<StateChangedEvent>
}
