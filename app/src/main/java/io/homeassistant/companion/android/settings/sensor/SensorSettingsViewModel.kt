package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.sensors.SensorReceiver
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorSettingsViewModel @Inject constructor(
    sensorDao: SensorDao,
    application: Application
) :
    AndroidViewModel(application) {

    enum class SensorFilter {
        ALL,
        ENABLED,
        DISABLED
    }

    private var sensorsList = emptyList<Sensor>()
    var sensors by mutableStateOf<Map<String, Sensor>>(emptyMap())
        private set

    var allSensors by mutableStateOf<Map<SensorManager, List<SensorManager.BasicSensor>>>(emptyMap())
        private set

    var searchQuery: String? = null
    var sensorFilter by mutableStateOf(SensorFilter.ALL)
        private set

    init {
        viewModelScope.launch {
            sensorDao.getAllFlow().collect {
                sensorsList = it
                filterSensorsList()
            }
        }
    }

    fun setSensorsSearchQuery(query: String? = "") {
        viewModelScope.launch {
            searchQuery = query
            filterSensorsList()
        }
    }

    fun setSensorFilterChoice(filter: SensorFilter) {
        viewModelScope.launch {
            sensorFilter = filter
            filterSensorsList()
        }
    }

    private suspend fun filterSensorsList() {
        val app = getApplication<Application>()
        val managers = SensorReceiver.MANAGERS.sortedBy { app.getString(it.name) }
        var availableSensors: List<SensorManager.BasicSensor>
        sensors = SensorReceiver.MANAGERS
            .filter { it.hasSensor(app.applicationContext) }
            .flatMap { manager ->
                manager.getAvailableSensors(app.applicationContext)
                    .filter { sensor ->
                        (
                            searchQuery.isNullOrEmpty() ||
                                (
                                    app.getString(sensor.name).contains(searchQuery!!, true) ||
                                        app.getString(manager.name).contains(searchQuery!!, true)
                                    )
                            ) &&
                            (
                                sensorFilter == SensorFilter.ALL ||
                                    (sensorFilter == SensorFilter.ENABLED && manager.isEnabled(app.applicationContext, sensor.id)) ||
                                    (sensorFilter == SensorFilter.DISABLED && !manager.isEnabled(app.applicationContext, sensor.id))
                                )
                    }
                    .mapNotNull { sensor -> sensorsList.firstOrNull { it.id == sensor.id } }
            }
            .associateBy { it.id }

        allSensors = managers.associateWith { manager ->
            availableSensors = manager.getAvailableSensors(app)
                .filter { basicSensor ->
                    sensors.containsKey(basicSensor.id)
                }
                .sortedBy { app.getString(it.name) }.distinct()
            availableSensors
        }
    }
}
