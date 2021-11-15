package io.homeassistant.companion.android.home

import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.data.SimplifiedEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomePresenterImpl @Inject constructor(
    private val view: HomeView,
    private val authenticationUseCase: AuthenticationRepository,
    private val integrationUseCase: IntegrationRepository
) : HomePresenter {

    companion object {
        val toggleDomains = listOf(
            "cover", "fan", "humidifier", "input_boolean", "light",
            "media_player", "remote", "siren", "switch"
        )
        val supportedDomains = listOf(
            "input_boolean", "light", "switch", "script", "scene"
        )
        const val TAG = "HomePresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

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

    override suspend fun getEntities(): List<Entity<*>> {
        return try {
            integrationUseCase.getEntities()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get entities", e)
            emptyList()
        }
    }

    override suspend fun getEntityUpdates(): Flow<Entity<*>> {
        return integrationUseCase.getEntityUpdates()
    }

    override suspend fun onEntityClicked(entityId: String) {

        if (entityId.split(".")[0] in toggleDomains) {
            integrationUseCase.callService(
                entityId.split(".")[0],
                "toggle",
                hashMapOf("entity_id" to entityId)
            )
        } else {
            integrationUseCase.callService(
                entityId.split(".")[0],
                "turn_on",
                hashMapOf("entity_id" to entityId)
            )
        }
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

    override suspend fun getWearHomeFavorites(): List<String> {
        return integrationUseCase.getWearHomeFavorites().toList()
    }

    override suspend fun setWearHomeFavorites(favorites: List<String>) {
        integrationUseCase.setWearHomeFavorites(favorites.toSet())
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
}
