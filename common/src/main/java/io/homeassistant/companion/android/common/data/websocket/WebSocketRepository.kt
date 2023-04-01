package io.homeassistant.companion.android.common.data.websocket

import dagger.assisted.AssistedFactory
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.websocket.impl.WebSocketRepositoryImpl
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ConversationResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CurrentUserResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DomainResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TemplateUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetTlvResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import kotlinx.coroutines.flow.Flow

interface WebSocketRepository {
    fun getConnectionState(): WebSocketState?
    fun shutdown()
    suspend fun sendPing(): Boolean
    suspend fun getCurrentUser(): CurrentUserResponse?
    suspend fun getConfig(): GetConfigResponse?
    suspend fun getStates(): List<EntityResponse<Any>>?
    suspend fun getAreaRegistry(): List<AreaRegistryResponse>?
    suspend fun getDeviceRegistry(): List<DeviceRegistryResponse>?
    suspend fun getEntityRegistry(): List<EntityRegistryResponse>?
    suspend fun getServices(): List<DomainResponse>?
    suspend fun getStateChanges(): Flow<StateChangedEvent>?
    suspend fun getStateChanges(entityIds: List<String>): Flow<TriggerEvent>?
    suspend fun getCompressedStateAndChanges(): Flow<CompressedStateChangedEvent>?
    suspend fun getCompressedStateAndChanges(entityIds: List<String>): Flow<CompressedStateChangedEvent>?
    suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>?
    suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>?
    suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>?
    suspend fun getTemplateUpdates(template: String): Flow<TemplateUpdatedEvent>?
    suspend fun getNotifications(): Flow<Map<String, Any>>?
    suspend fun ackNotification(confirmId: String): Boolean
    suspend fun commissionMatterDevice(code: String): MatterCommissionResponse?
    suspend fun commissionMatterDeviceOnNetwork(pin: Long): MatterCommissionResponse?
    suspend fun getThreadDatasets(): List<ThreadDatasetResponse>?
    suspend fun getThreadDatasetTlv(datasetId: String): ThreadDatasetTlvResponse?
    suspend fun addThreadDataset(tlv: ByteArray): Boolean
    suspend fun getConversation(speech: String): ConversationResponse?
}

@AssistedFactory
interface WebSocketRepositoryFactory {
    fun create(serverId: Int): WebSocketRepositoryImpl
}
