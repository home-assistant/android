package io.homeassistant.companion.android.onboarding.locationforsecureconnection

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.onboarding.locationforsecureconnection.navigation.LocationForSecureConnectionRoute
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class LocationForSecureConnectionViewModel @VisibleForTesting constructor(
    private val serverId: Int,
    private val serverManager: ServerManager,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        serverManager: ServerManager,
    ) : this(savedStateHandle.toRoute<LocationForSecureConnectionRoute>().serverId, serverManager)

    fun allowInsecureConnection(allowInsecureConnection: Boolean) {
        viewModelScope.launch {
            try {
                serverManager.integrationRepository(serverId).setAllowInsecureConnection(allowInsecureConnection)
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "Something went wrong while setting AllowInsecureConnection to $allowInsecureConnection for server $serverId",
                )
            }
        }
    }
}
