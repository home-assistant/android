package io.homeassistant.companion.android.home

import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
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
    private val webSocketUseCase: WebSocketRepository
) : HomePresenter {

    companion object {
        val toggleDomains = listOf(
            "cover", "fan", "humidifier", "input_boolean", "light", "lock",
            "media_player", "remote", "siren", "switch"
        )
        val domainsWithNames = mapOf(
            "input_boolean" to commonR.string.input_booleans,
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

    override suspend fun getEntityUpdates(): Flow<Entity<*>>? {
        return integrationUseCase.getEntityUpdates()
    }

    override suspend fun onEntityClicked(entityId: String, state: String) {
        val domain = entityId.split(".")[0]
        val serviceName = when (domain) {
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
        integrationUseCase.callService(
            domain,
            serviceName,
            hashMapOf("entity_id" to entityId)
        )
    }

    override fun onLogoutClicked() {
        mainScope.launch {
            authenticationUseCase.revokeSession()
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
        return integrationUseCase.getTileShortcuts().map { SimplifiedEntity(it) }
    }

    override suspend fun setTileShortcuts(entities: List<SimplifiedEntity>) {
        integrationUseCase.setTileShortcuts(entities.map { it.entityString })
    }

    override suspend fun getWearHapticFeedback(): Boolean {
        return integrationUseCase.getWearHapticFeedback()
    }

    override suspend fun setWearHapticFeedback(enabled: Boolean) {
        integrationUseCase.setWearHapticFeedback(enabled)
    }

    override suspend fun getWearToastConfirmation(): Boolean {
        return integrationUseCase.getWearToastConfirmation()
    }

    override suspend fun setWearToastConfirmation(enabled: Boolean) {
        integrationUseCase.setWearToastConfirmation(enabled)
    }

    override suspend fun getShowShortcutText(): Boolean {
        return integrationUseCase.getShowShortcutText()
    }

    override suspend fun setShowShortcutTextEnabled(enabled: Boolean) {
        integrationUseCase.setShowShortcutTextEnabled(enabled)
    }

    override suspend fun getTemplateTileContent(): String {
        return integrationUseCase.getTemplateTileContent()
    }

    override suspend fun setTemplateTileContent(content: String) {
        integrationUseCase.setTemplateTileContent(content)
    }

    override suspend fun getTemplateTileRefreshInterval(): Int {
        return integrationUseCase.getTemplateTileRefreshInterval()
    }

    override suspend fun setTemplateTileRefreshInterval(interval: Int) {
        integrationUseCase.setTemplateTileRefreshInterval(interval)
    }
}
