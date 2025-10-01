package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.data.SimplifiedEntity
import kotlinx.coroutines.flow.Flow

interface HomePresenter {

    fun init(homeView: HomeView)

    fun onViewReady()
    suspend fun onEntityClicked(entityId: String, state: String)
    suspend fun onFanSpeedChanged(entityId: String, speed: Float)
    suspend fun onBrightnessChanged(entityId: String, brightness: Float)
    suspend fun onColorTempChanged(entityId: String, colorTemp: Float, isKelvin: Boolean)
    fun onLogoutClicked()
    fun onInvalidAuthorization()
    fun onFinish()

    suspend fun isConnected(): Boolean
    suspend fun getServerId(): Int?
    suspend fun getWebSocketState(): WebSocketState?

    suspend fun getEntities(): List<Entity>?
    suspend fun getEntityUpdates(entityIds: List<String>): Flow<Entity>?

    suspend fun getAreaRegistry(): List<AreaRegistryResponse>?
    suspend fun getDeviceRegistry(): List<DeviceRegistryResponse>?
    suspend fun getEntityRegistry(): List<EntityRegistryResponse>?
    suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>?
    suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>?
    suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>?

    suspend fun getAllTileShortcuts(): Map<Int?, List<SimplifiedEntity>>
    suspend fun getTileShortcuts(tileId: Int): List<SimplifiedEntity>
    suspend fun setTileShortcuts(tileId: Int?, entities: List<SimplifiedEntity>)

    suspend fun getWearHapticFeedback(): Boolean
    suspend fun setWearHapticFeedback(enabled: Boolean)
    suspend fun getWearToastConfirmation(): Boolean
    suspend fun setWearToastConfirmation(enabled: Boolean)
    suspend fun getShowShortcutText(): Boolean
    suspend fun setShowShortcutTextEnabled(enabled: Boolean)
    suspend fun getAllTemplateTiles(): Map<Int, TemplateTileConfig>
    suspend fun setTemplateTileRefreshInterval(tileId: Int, interval: Int)

    suspend fun getWearFavoritesOnly(): Boolean
    suspend fun setWearFavoritesOnly(enabled: Boolean)
}
