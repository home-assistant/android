package io.homeassistant.companion.android.common.data.websocket

import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DomainResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import kotlinx.coroutines.flow.Flow

interface WebSocketRepository {
    fun getConnectionState(): WebSocketState?
    suspend fun sendPing(): Boolean
    suspend fun getConfig(): GetConfigResponse?
    suspend fun getStates(): List<EntityResponse<Any>>?
    suspend fun getAreaRegistry(): List<AreaRegistryResponse>?
    suspend fun getDeviceRegistry(): List<DeviceRegistryResponse>?
    suspend fun getEntityRegistry(): List<EntityRegistryResponse>?
    suspend fun getServices(): List<DomainResponse>?
    suspend fun getStateChanges(): Flow<StateChangedEvent>?
    suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>?
    suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>?
    suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>?
    suspend fun getNotifications(): Flow<Map<String, Any>>?
    suspend fun ackNotification(confirmId: String): Boolean
}
