package io.homeassistant.companion.android.webview.addto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// TODO why do we have to use Assisted for all params
@HiltViewModel(assistedFactory = AddToViewModel.Factory::class)
class AddToViewModel @AssistedInject constructor(
    @Assisted val entityId: String,
    @Assisted val serverManager: ServerManager,
    @Assisted val prefsRepository: PrefsRepository,
) : ViewModel() {
    private val _potentialActions = MutableStateFlow<List<AddToAction>>(emptyList())
    val potentialActions = _potentialActions.asStateFlow()

    init {
        viewModelScope.launch {
            serverManager.getServer()?.let { server ->
                serverManager.integrationRepository(server.id).getEntity(entityId)
                    ?.let { entity ->
                        val actions = mutableListOf<AddToAction>()

                        if (isVehicleDomain(entity)) {
                            // We could check if it already exist but the action won't do anything so we can keep it
                            actions.add(AddToAction.AndroidAutoFavorite)
                        }
//                        if (isValidTileDomain(entity)) {
//                            // TODO check if there is a Tile available
//                            actions.add(AddToAction.Tile)
//                        }

                        // TODO do watch based on all the watch available

                        // TODO shortcut we need to know if we can add a shortcut or not
                        // If we already have 5 shortcuts set we probably should not offer this option

                        actions.add(AddToAction.EntityWidget)

                        if (entity.domain == MEDIA_PLAYER_DOMAIN) {
                            actions.add(AddToAction.MediaPlayerWidget)
                        }

                        if (entity.domain == TODO_DOMAIN) {
                            actions.add(AddToAction.TodoWidget)
                        }

                        if (entity.domain == CAMERA_DOMAIN || entity.domain == IMAGE_DOMAIN) {
                            actions.add(AddToAction.CameraWidget)
                        }
                        _potentialActions.emit(actions)
                    } ?: FailFast.fail { "Entity is null" }
            }
        }
    }

    fun addToAndroidAutoFavorite() {
        viewModelScope.launch {
            serverManager.getServer()?.id?.let { serverId ->
                prefsRepository.addAutoFavorite(AutoFavorite(serverId, entityId))
            } ?: FailFast.fail { "Server is null" }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(entityId: String, serverManager: ServerManager, prefsRepository: PrefsRepository): AddToViewModel
    }
}
