package io.homeassistant.companion.android.webview.addto

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.util.vehicle.isValidTileDomain
import io.homeassistant.companion.android.util.vehicle.isVehicleDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AddToAction {
    @get:DrawableRes
    val imageRes: Int

    // TODO maybe replace this with ID + args
    @Composable
    fun text(): String

    data object AndroidAutoFavorite : AddToAction {
        override val imageRes: Int
            get() = R.drawable.ic_car

        @Composable
        override fun text(): String {
            return "Auto favorite" // stringResource()
        }
    }

    data object Shortcut : AddToAction {
        override val imageRes: Int
            get() = commonR.drawable.ic_shortcut

        @Composable
        override fun text(): String {
            return "Shortcut"
        }
    }

    data object Tile : AddToAction {
        override val imageRes: Int
            get() = commonR.drawable.ic_tile

        @Composable
        override fun text(): String {
            return "Tile"
        }
    }

    data object Widget : AddToAction {
        override val imageRes: Int
            get() = R.drawable.ic_widget

        @Composable
        override fun text(): String {
            return "Widget"
        }
    }

    data object Watch : AddToAction {
        override val imageRes: Int
            get() = R.drawable.ic_baseline_watch_24

        @Composable
        override fun text(): String {
            return "Watch"
        }
    }
}

// TODO why do we have to use Assisted for all params
@HiltViewModel(assistedFactory = AddToViewModel.Factory::class)
class AddToViewModel @AssistedInject constructor(
    @Assisted val entityId: String,
    @Assisted val serverManager: ServerManager,
    @Assisted val prefsRepository: PrefsRepository,
) : ViewModel() {

    private val _entityName = MutableStateFlow("")
    val entityName = _entityName.asStateFlow()

    private val _potentialActions = MutableStateFlow<List<AddToAction>>(emptyList())
    val potentialActions = _potentialActions.asStateFlow()

    init {
        viewModelScope.launch {
            serverManager.getServer()?.let { server ->
                serverManager.integrationRepository(server.id).getEntity(entityId)
                    ?.let { entity ->
                        _entityName.emit(entity.friendlyName)
                        val actions = mutableListOf<AddToAction>()

                        if (isVehicleDomain(entity)) {
                            // We could check if it already exist but the action won't do anything so we can keep it
                            actions.add(AddToAction.AndroidAutoFavorite)
                        }
                        if (isValidTileDomain(entity)) {
                            // TODO check if there is a Tile available
                            actions.add(AddToAction.Tile)
                        }

                        // TODO do watch

                        _potentialActions.emit(actions)
                    } ?: FailFast.fail { "Entity is null" }
            }
        }
    }

    fun addToAndroidAutoFavorite() {
        viewModelScope.launch {
            val currentFavorites = prefsRepository.getAutoFavorites()
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
