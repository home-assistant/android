package io.homeassistant.companion.android.home

import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.data.SimplifiedEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

class HomePresenterImpl @Inject constructor(
    private val authenticationUseCase: AuthenticationRepository,
    private val integrationUseCase: IntegrationRepository,
    private val webSocketUseCase: WebSocketRepository,
    private val wearPrefsRepository: WearPrefsRepository
) : HomePresenter {

    companion object {
        val toggleDomains = listOf(
            "cover", "fan", "humidifier", "input_boolean", "light", "lock",
            "media_player", "remote", "siren", "switch"
        )
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
            val sessionValid = authenticationUseCase.getSessionState() == SessionState.CONNECTED
            if (sessionValid && integrationUseCase.isRegistered()) {
                resyncRegistration()
            } else if (sessionValid) {
                view.displayMobileAppIntegration()
            } else {
                view.displayOnBoarding()
            }
        }
    }

    override suspend fun getEntities(): List<Entity<*>>? {
        return integrationUseCase.getEntities()
    }

    override suspend fun getEntityUpdates(entityIds: List<String>): Flow<Entity<*>>? {
        return integrationUseCase.getEntityUpdates(entityIds)
    }

    override suspend fun onEntityClicked(entityId: String, state: String) {
        val domain = entityId.split(".")[0]
        val serviceName = when (domain) {
            "button", "input_button" -> "press"
            "lock" -> {
                // Defaults to locking, to be save
                if (state == "locked")
                    "unlock"
                else
                    "lock"
            }
            in toggleDomains -> "toggle"
            else -> "turn_on"
        }
        try {
            integrationUseCase.callService(
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
            integrationUseCase.callService(
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
            integrationUseCase.callService(
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

    override suspend fun onColorTempChanged(entityId: String, colorTemp: Float) {
        try {
            integrationUseCase.callService(
                entityId.split(".")[0],
                "turn_on",
                hashMapOf(
                    "entity_id" to entityId,
                    "color_temp" to colorTemp.toInt()
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
            try {
                authenticationUseCase.revokeSession()
            } catch (e: Exception) {
                Log.e(TAG, "Exception while revoking session", e)
                // Remove local data anyway, the user wants to sign out and we don't need the server for that
                authenticationUseCase.removeSessionData()
            }
            view.displayOnBoarding()
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    private fun resyncRegistration() {
        mainScope.launch {
            try {
                integrationUseCase.updateRegistration(
                    DeviceRegistration(
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        null,
                        null
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Issue updating Registration", e)
            }
        }
    }

    override suspend fun isConnected(): Boolean {
        return integrationUseCase.isRegistered()
    }

    override fun getWebSocketState(): WebSocketState? {
        return webSocketUseCase.getConnectionState()
    }

    override suspend fun getAreaRegistry(): List<AreaRegistryResponse>? {
        return webSocketUseCase.getAreaRegistry()
    }

    override suspend fun getDeviceRegistry(): List<DeviceRegistryResponse>? {
        return webSocketUseCase.getDeviceRegistry()
    }

    override suspend fun getEntityRegistry(): List<EntityRegistryResponse>? {
        return webSocketUseCase.getEntityRegistry()
    }

    override suspend fun getAreaRegistryUpdates(): Flow<AreaRegistryUpdatedEvent>? {
        return webSocketUseCase.getAreaRegistryUpdates()
    }

    override suspend fun getDeviceRegistryUpdates(): Flow<DeviceRegistryUpdatedEvent>? {
        return webSocketUseCase.getDeviceRegistryUpdates()
    }

    override suspend fun getEntityRegistryUpdates(): Flow<EntityRegistryUpdatedEvent>? {
        return webSocketUseCase.getEntityRegistryUpdates()
    }

    override suspend fun getTileShortcuts(): List<SimplifiedEntity> {
        return wearPrefsRepository.getTileShortcuts().map { SimplifiedEntity(it) }
    }

    override suspend fun setTileShortcuts(entities: List<SimplifiedEntity>) {
        wearPrefsRepository.setTileShortcuts(entities.map { it.entityString })
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

    override suspend fun setTemplateTileContent(content: String) {
        wearPrefsRepository.setTemplateTileContent(content)
    }

    override suspend fun getTemplateTileRefreshInterval(): Int {
        return wearPrefsRepository.getTemplateTileRefreshInterval()
    }

    override suspend fun setTemplateTileRefreshInterval(interval: Int) {
        wearPrefsRepository.setTemplateTileRefreshInterval(interval)
    }
}
