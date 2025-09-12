package io.homeassistant.companion.android.onboarding.locationsharing

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.onboarding.locationsharing.navigation.LocationSharingRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal sealed interface LocationSharingNavigationEvent {
    data object GoToNextScreen : LocationSharingNavigationEvent
}

// TODO tomorrow
// Finish the second screen
// Write all the tests needed
// VM + navigation + screen + screenshots

@HiltViewModel
internal class LocationSharingViewModel @VisibleForTesting constructor(
    private val serverId: Int,
    private val sensorDao: SensorDao,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        sensorDao: SensorDao,
    ) : this(serverId = savedStateHandle.toRoute<LocationSharingRoute>().serverId, sensorDao)

    private val _navigationEventsFlow = MutableSharedFlow<LocationSharingNavigationEvent>(replay = 1)
    val navigationEventsFlow = _navigationEventsFlow.asSharedFlow()

    fun onGoToNextScreen() {
        viewModelScope.launch {
            _navigationEventsFlow.emit(LocationSharingNavigationEvent.GoToNextScreen)
        }
    }

    fun setupLocationSensor(enabled: Boolean) {
        viewModelScope.launch {
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
        }
    }
}
