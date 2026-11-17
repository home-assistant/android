package io.homeassistant.companion.android.home

import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.data.SimplifiedEntity

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

    suspend fun getAllTileShortcuts(): Map<Int?, List<SimplifiedEntity>>
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
