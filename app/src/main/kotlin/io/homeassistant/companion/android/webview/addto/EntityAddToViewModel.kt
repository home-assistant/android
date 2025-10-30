package io.homeassistant.companion.android.webview.addto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.IMAGE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.util.vehicle.isVehicleDomain
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class EntityAddToViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val prefsRepository: PrefsRepository,
) : ViewModel() {

    suspend fun actionsForEntity(entityId: String): List<EntityAddToAction> {
        return withContext(Dispatchers.Default) {
            val actions = mutableListOf<EntityAddToAction>()
            serverManager.getServer()?.let { server ->
                serverManager.integrationRepository(server.id).getEntity(entityId)
                    ?.let { entity ->
                        actions.add(EntityAddToAction.EntityWidget)

                        if (isVehicleDomain(entity)) {
                            // We could check if it already exist but the action won't do anything so we can keep it
                            actions.add(EntityAddToAction.AndroidAutoFavorite)
                        }

                        if (entity.domain == MEDIA_PLAYER_DOMAIN) {
                            actions.add(EntityAddToAction.MediaPlayerWidget)
                        }

                        if (entity.domain == TODO_DOMAIN) {
                            actions.add(EntityAddToAction.TodoWidget)
                        }

                        if (entity.domain == CAMERA_DOMAIN || entity.domain == IMAGE_DOMAIN) {
                            actions.add(EntityAddToAction.CameraWidget)
                        }

                        // TODO support tile https://github.com/home-assistant/android/issues/5623
                        // if (isValidTileDomain(entity)) {
                        //     actions.add(AddToAction.Tile)
                        // }

                        // TODO support watch favorite https://github.com/home-assistant/android/issues/5624
                        // when it has a watch always send it and set the flag to false when not connected + details that it is disconnected

                        // TODO support shortcut https://github.com/home-assistant/android/issues/5625
                        // Always show but send false and details about why it is not enabled
                    }
            }
            actions
        }
    }

    fun addToAndroidAutoFavorite(entityId: String) {
        viewModelScope.launch {
            serverManager.getServer()?.id?.let { serverId ->
                prefsRepository.addAutoFavorite(AutoFavorite(serverId, entityId))
            } ?: FailFast.fail { "Server is null" }
        }
    }
}
