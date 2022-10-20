package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    var sensors = mutableStateListOf<Sensor>()

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
        searchQuery = query
        filterSensorsList()
    }

    fun setSensorFilterChoice(filter: SensorFilter) {
        sensorFilter = filter
        filterSensorsList()
    }

    private fun filterSensorsList() {
        val app = getApplication<Application>()
        sensors.clear()

        SensorReceiver.MANAGERS.filter { it.hasSensor(app.applicationContext) }.forEach { manager ->
            sensors.addAll(
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
            )
        }
    }
}
