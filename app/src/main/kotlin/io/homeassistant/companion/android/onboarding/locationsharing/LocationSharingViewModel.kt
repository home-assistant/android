package io.homeassistant.companion.android.onboarding.locationsharing

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.LocationSharingRoute
import io.homeassistant.companion.android.sensors.LocationSensorManager
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
internal class LocationSharingViewModel @VisibleForTesting constructor(
    private val serverId: Int,
    private val sensorRepository: SensorRepository,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        sensorRepository: SensorRepository,
    ) : this(serverId = savedStateHandle.toRoute<LocationSharingRoute>().serverId, sensorRepository)

    fun setupLocationSensor(enabled: Boolean) {
        viewModelScope.launch {
            try {
                sensorRepository.setSensorsEnabled(
                    sensorIds = listOf(
                        LocationSensorManager.backgroundLocation.id,
                        LocationSensorManager.zoneLocation.id,
                        LocationSensorManager.singleAccurateLocation.id,
                    ),
                    serverId = serverId,
                    enabled = enabled,
                )
            } catch (e: Exception) {
                Timber.e(e, "Something went wrong while setting the location sensor")
            }
        }
    }
}
