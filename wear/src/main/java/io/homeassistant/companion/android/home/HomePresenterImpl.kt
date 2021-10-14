package io.homeassistant.companion.android.home

import android.util.Log
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomePresenterImpl @Inject constructor(
    private val view: HomeView,
    private val authenticationUseCase: AuthenticationRepository,
    private val integrationUseCase: IntegrationRepository
) : HomePresenter {

    companion object {
        const val TAG = "HomePresenter"
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onViewReady() {
        mainScope.launch {
            val sessionValid = authenticationUseCase.getSessionState() == SessionState.CONNECTED
            if (sessionValid && integrationUseCase.isRegistered()) {
                resyncRegistration()
                // We'll stay on HomeActivity, so start loading entities
                processEntities(integrationUseCase.getEntities())
            } else if (sessionValid) {
                view.displayMobileAppIntegration()
            } else {
                view.displayOnBoarding()
            }
        }
    }

    override fun onEntityClicked(entity: Entity<Any>) {

        if (entity.entityId.split(".")[0] == "light") {
            mainScope.launch {
                integrationUseCase.callService(
                    entity.entityId.split(".")[0],
                    "toggle",
                    hashMapOf("entity_id" to entity.entityId)
                )
            }
        } else {
            mainScope.launch {
                integrationUseCase.callService(
                    entity.entityId.split(".")[0],
                    "turn_on",
                    hashMapOf("entity_id" to entity.entityId)
                )
            }
        }
    }

    override fun onButtonClicked(id: String) {
        if (id == HomeListAdapter.BUTTON_ID_LOGOUT) {
            onLogoutClicked()
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

    private fun processEntities(entities: Array<Entity<Any>>) {
        val scenes = entities.sortedBy { it.entityId }.filter { it.entityId.split(".")[0] == "scene" }
        val scripts = entities.sortedBy { it.entityId }.filter { it.entityId.split(".")[0] == "script" }
        val lights = entities.sortedBy { it.entityId }.filter { it.entityId.split(".")[0] == "light" }
        val covers = entities.sortedBy { it.entityId }.filter { it.entityId.split(".")[0] == "cover" }
        view.showHomeList(scenes, scripts, lights, covers)
        Log.i(TAG, "Cover data: " + covers[0].attributes.toString())
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
}
