package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.sensors.SensorReceiver
import kotlinx.coroutines.launch
import javax.inject.Inject

class SensorSettingsViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {

    private val sensorsDao = AppDatabase.getInstance(getApplication()).sensorDao()
    private var sensorsList = emptyList<Sensor>()
    var sensors = mutableStateListOf<Sensor>()

    var searchQuery: String? = null
    var showOnlyEnabledSensors = mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            sensorsDao.getAllFlow().collect {
                sensorsList = it
                filterSensorsList()
            }
        }
    }

    fun setSensorsSearchQuery(query: String? = "") {
        searchQuery = query
        filterSensorsList()
    }

    fun setShowOnlyEnabledSensors(onlyEnabled: Boolean) {
        showOnlyEnabledSensors.value = onlyEnabled
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
                            (!showOnlyEnabledSensors.value || manager.isEnabled(app.applicationContext, sensor.id))
                    }
                    .mapNotNull { sensor -> sensorsList.firstOrNull { it.id == sensor.id } }
            )
        }
    }
}
