package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.HomeAssistantApplication
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
    var availableSensors = emptyList<SensorManager.BasicSensor>()

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

    fun updateManagers(sensorManager: SensorManager) {
        val context = getApplication<HomeAssistantApplication>().applicationContext
        viewModelScope.launch {
            availableSensors = sensorManager.getAvailableSensors(context, null)
                .filter { basicSensor ->
                    sensors.any { basicSensor.id == it.value.id }
                }
                .sortedBy { context.getString(it.name) }.distinct()
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
        sensors = SensorReceiver.MANAGERS
            .filter { it.hasSensor(app.applicationContext) }
            .flatMap { manager ->
                manager.getAvailableSensors(app.applicationContext, null)
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
    }
}
