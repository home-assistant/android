package io.homeassistant.companion.android.settings.sensor

import android.app.Application
import androidx.annotation.IdRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.sensors.SensorReceiver
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SensorSettingsViewModel @Inject constructor(sensorDao: SensorDao, application: Application) :
    AndroidViewModel(application) {

    enum class SensorFilter(@IdRes val menuItemId: Int) {
        ALL(R.id.action_show_sensors_all),
        ENABLED(R.id.action_show_sensors_enabled),
        DISABLED(R.id.action_show_sensors_disabled),
        ;

        companion object {
            val menuItemIdToFilter = values().associateBy { it.menuItemId }
        }
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
                withContext(Dispatchers.IO) {
                    // Compare contents, because the worker typically pushes a DB update on
                    // sensor updates even when contents don't change
                    val different = sensorsList != it
                    sensorsList = it
                    if (different) filterSensorsList()
                }
            }
        }
    }

    fun setSensorsSearchQuery(query: String? = "") {
        viewModelScope.launch {
            searchQuery = query
            filterSensorsList()
        }
    }

    fun setSensorFilterChoice(@IdRes filterMenuItemId: Int) {
        viewModelScope.launch {
            sensorFilter = SensorFilter.menuItemIdToFilter.getValue(filterMenuItemId)
            filterSensorsList()
        }
    }

    private suspend fun filterSensorsList() = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val managers = SensorReceiver.MANAGERS.sortedBy { app.getString(it.name) }
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
                                    (
                                        sensorFilter == SensorFilter.ENABLED &&
                                            manager.isEnabled(app.applicationContext, sensor)
                                        ) ||
                                    (
                                        sensorFilter == SensorFilter.DISABLED &&
                                            !manager.isEnabled(app.applicationContext, sensor)
                                        )
                                )
                    }
                    .mapNotNull { sensor ->
                        sensorsList.filter { it.id == sensor.id }
                            .maxByOrNull { it.enabled } // If any server is enabled, show the value
                    }
            }
            .associateBy { it.id }

        allSensors = managers.associateWith { manager ->
            manager.getAvailableSensors(app)
                .filter { basicSensor ->
                    sensors.containsKey(basicSensor.id)
                }
                .sortedBy { app.getString(it.name) }.distinct()
        }
    }
}
