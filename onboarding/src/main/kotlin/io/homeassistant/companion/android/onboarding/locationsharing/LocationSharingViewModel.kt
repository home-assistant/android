package io.homeassistant.companion.android.onboarding.locationsharing

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.LocationSharingRoute
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

// TODO Monday
// Remove RadioGroup from the commit to avoid rebase nightmare
// Write all the tests needed
// VM + navigation + screen + screenshots

@HiltViewModel
internal class LocationSharingViewModel @VisibleForTesting constructor(
    private val serverId: Int,
    private val sensorDao: SensorDao,
    private val serverManager: ServerManager,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        sensorDao: SensorDao,
        serverManager: ServerManager,
    ) : this(serverId = savedStateHandle.toRoute<LocationSharingRoute>().serverId, sensorDao, serverManager)

    fun setupLocationSensor(enabled: Boolean) {
        viewModelScope.launch {
            try {
                sensorDao.setSensorsEnabled(
                    sensorIds = listOf(
                        // TODO add sensor ID from `LocationSensorManager` instead of string
                        "location_background",
                        "zone_background",
                        "accurate_location",
                    ),
                    serverId = serverId,
                    enabled = enabled,
                )
                // We don't allow the insecure connection if the user give his permission to use location.
                // This can be overridden in th LocationForSecureConnectionScreen.
                serverManager.integrationRepository(serverId).setAllowInsecureConnection(!enabled)
            } catch (e: Exception) {
                Timber.e(e, "Something went wrong while setting the location sensor")
            }
        }
    }
}
