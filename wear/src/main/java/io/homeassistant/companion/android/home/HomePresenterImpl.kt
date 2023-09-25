package io.homeassistant.companion.android.home

import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.onboarding.getMessagingToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

class HomePresenterImpl @Inject constructor(
    private val serverManager: ServerManager,
    private val wearPrefsRepository: WearPrefsRepository
) : HomePresenter {

    companion object {
        val domainsWithNames = mapOf(
            "button" to commonR.string.buttons,
            "cover" to commonR.string.covers,
            "fan" to commonR.string.fans,
            "input_boolean" to commonR.string.input_booleans,
            "input_button" to commonR.string.input_buttons,
            "light" to commonR.string.lights,
            "lock" to commonR.string.locks,
            "switch" to commonR.string.switches,
            "script" to commonR.string.scripts,
            "scene" to commonR.string.scenes
        )
        val supportedDomains = domainsWithNames.keys.toList()
        const val TAG = "HomePresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var view: HomeView

    override fun init(homeView: HomeView) {
        view = homeView
    }

    override fun onViewReady() {
        mainScope.launch {
            // Remove any invalid servers (incomplete, partly migrated from another device)
            serverManager.defaultServers
                .filter { serverManager.authenticationRepository(it.id).getSessionState() == SessionState.ANONYMOUS }
                .forEach { serverManager.removeServer(it.id) }

            if (
                serverManager.isRegistered() &&
                serverManager.authenticationRepository().getSessionState() == SessionState.CONNECTED
            ) {
                resyncRegistration()
            } else {
                view.displayOnBoarding()
            }
        }
    }

    override suspend fun getEntities(): List<Entity<*>>? {
        return serverManager.integrationRepository().getEntities()
    }

    override suspend fun getEntityUpdates(entityIds: List<String>): Flow<Entity<*>>? {
        return serverManager.integrationRepository().getEntityUpdates(entityIds)
    }

    override suspend fun onEntityClicked(entityId: String, state: String) {
        val domain = entityId.split(".")[0]
        val serviceName = when (domain) {
            "button", "input_button" -> "press"
            "lock" -> {
                // Defaults to locking, to be save
                if (state == "locked") {
                    "unlock"
                } else {
                    "lock"
                }
            }
            in EntityExt.DOMAINS_TOGGLE -> "toggle"
            else -> "turn_on"
        }
        try {
            serverManager.integrationRepository().callService(
                domain,
                serviceName,
                hashMapOf("entity_id" to entityId)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception when toggling entity", e)
        }
    }

    override suspend fun onFanSpeedChanged(entityId: String, speed: Float) {
        try {
            serverManager.integrationRepository().callService(
                entityId.split(".")[0],
                "set_percentage",
                hashMapOf(
                    "entity_id" to entityId,
                    "percentage" to speed.toInt()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception when setting fan speed", e)
        }
    }

    override suspend fun onBrightnessChanged(entityId: String, brightness: Float) {
        try {
            serverManager.integrationRepository().callService(
                entityId.split(".")[0],
                "turn_on",
                hashMapOf(
                    "entity_id" to entityId,
                    "brightness" to brightness.toInt()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception when setting light brightness", e)
        }
    }

    override suspend fun onColorTempChanged(entityId: String, colorTemp: Float, isKelvin: Boolean) {
        try {
            val colorTempKey = if (isKelvin) "color_temp_kelvin" else "color_temp"
            serverManager.integrationRepository().callService(
                entityId.split(".")[0],
                "turn_on",
                hashMapOf(
                    "entity_id" to entityId,
                    colorTempKey to colorTemp.toInt()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception when setting light color temp", e)
        }
    }

    override fun onInvalidAuthorization() = finishSession()

    override fun onLogoutClicked() = finishSession()

    private fun finishSession() {
        mainScope.launch {
            serverManager.getServer()?.let {
                try {
                    serverManager.authenticationRepository(it.id).revokeSession()
                    serverManager.removeServer(it.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while revoking session", e)
                    // Remove local data anyway, the user wants to sign out and we don't need the server for that
                    serverManager.removeServer(it.id)
                }
            }
            view.displayOnBoarding()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    private fun resyncRegistration() {
        serverManager.defaultServers.forEach {
            mainScope.launch {
                try {
                    serverManager.integrationRepository(it.id).updateRegistration(
                        DeviceRegistration(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            null,
                            getMessagingToken(),
                            false
                        )
                    )
                    serverManager.webSocketRepository(it.id).getCurrentUser() // Update cached data
                } catch (e: Exception) {
                    Log.e(TAG, "Issue updating Registration", e)
                }
            }
        }
    }

    override fun isConnected(): Boolean = serverManager.isRegistered()

    override fun getServerId(): Int? = serverManager.getServer()?.id

    override fun getWebSocketState(): WebSocketState? {
        return serverManager.webSocketRepository().getConnectionState()
    }

    override suspend fun getAreaRegistry(): List<AreaRegistryResponse>? {
        return serverManager.webSocketRepository().getAreaRegistry()
    }

    override suspend fun getDeviceRegistry(): List<DeviceRegistryResponse>? {
        return serverManager.webSocketRepository().getDeviceRegistry()
    }

    override suspend fun getEntityRegistry(): List<EntityRegistryResponse>? {
        return serverManager.webSocketRepository().getEntityRegistry()
    }

    override suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>? {
        return serverManager.webSocketRepository().getAreaRegistryUpdates()
    }

    override suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>? {
        return serverManager.webSocketRepository().getDeviceRegistryUpdates()
    }

    override suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>? {
        return serverManager.webSocketRepository().getEntityRegistryUpdates()
    }

    override suspend fun getAllTileShortcuts(): Map<Int?, List<SimplifiedEntity>> {
        return wearPrefsRepository.getAllTileShortcuts().mapValues { (_, entities) ->
            entities.map {
                SimplifiedEntity(it)
            }
        }
    }

    override suspend fun getTileShortcuts(tileId: Int): List<SimplifiedEntity> {
        return wearPrefsRepository.getTileShortcutsAndSaveTileId(tileId).map { SimplifiedEntity(it) }
    }

    override suspend fun setTileShortcuts(tileId: Int?, entities: List<SimplifiedEntity>) {
        wearPrefsRepository.setTileShortcuts(tileId, entities.map { it.entityString })
    }

    override suspend fun getWearHapticFeedback(): Boolean {
        return wearPrefsRepository.getWearHapticFeedback()
    }

    override suspend fun setWearHapticFeedback(enabled: Boolean) {
        wearPrefsRepository.setWearHapticFeedback(enabled)
    }

    override suspend fun getWearToastConfirmation(): Boolean {
        return wearPrefsRepository.getWearToastConfirmation()
    }

    override suspend fun setWearToastConfirmation(enabled: Boolean) {
        wearPrefsRepository.setWearToastConfirmation(enabled)
    }

    override suspend fun getShowShortcutText(): Boolean {
        return wearPrefsRepository.getShowShortcutText()
    }

    override suspend fun setShowShortcutTextEnabled(enabled: Boolean) {
        wearPrefsRepository.setShowShortcutTextEnabled(enabled)
    }

    override suspend fun getTemplateTileContent(): String {
        return wearPrefsRepository.getTemplateTileContent()
    }

    override suspend fun getTemplateTileRefreshInterval(): Int {
        return wearPrefsRepository.getTemplateTileRefreshInterval()
    }

    override suspend fun setTemplateTileRefreshInterval(interval: Int) {
        wearPrefsRepository.setTemplateTileRefreshInterval(interval)
    }

    override suspend fun getWearFavoritesOnly(): Boolean {
        return wearPrefsRepository.getWearFavoritesOnly()
    }

    override suspend fun setWearFavoritesOnly(enabled: Boolean) {
        wearPrefsRepository.setWearFavoritesOnly(enabled)
    }
}
