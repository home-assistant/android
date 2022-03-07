package io.homeassistant.companion.android.settings.sensor

import android.Manifest
import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.sensors.NetworkSensorManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorWithAttributes
import io.homeassistant.companion.android.sensors.LastAppSensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class SensorDetailViewModel @Inject constructor(
    state: SavedStateHandle,
    private val integrationUseCase: IntegrationRepository,
    application: Application
) : AndroidViewModel(application) {

    companion object {
        const val TAG = "SensorDetailViewModel"

        data class LocationPermissionsDialog(
            val block: Boolean,
            val sensors: Array<String>,
            val permissions: Array<String>? = null
        )
    }

    val sensorId: String = state["id"]!!
    val app = application

    val permissionRequests = MutableLiveData<Array<String>>()
    val locationPermissionRequests = MutableLiveData<LocationPermissionsDialog?>()

    val sensorManager = SensorReceiver.MANAGERS
        .find { it.getAvailableSensors(app).any { sensor -> sensor.id == sensorId } }
    val basicSensor = sensorManager?.getAvailableSensors(app)
        ?.find { it.id == sensorId }

    private val sensorDao = AppDatabase.getInstance(app).sensorDao()
    private val sensorFlow = sensorDao.getFullFlow(sensorId)
    var sensor = mutableStateOf<SensorWithAttributes?>(null)
        private set
    private val sensorSettingsFlow = sensorDao.getSettingsFlow(sensorId)
    var sensorSettings = mutableStateListOf<SensorSetting>()
        private set

    val zones by lazy {
        Log.d(TAG, "Get zones from Home Assistant for listing zones in preferences...")
        runBlocking {
            try {
                val cachedZones = integrationUseCase.getZones().map { it.entityId }
                Log.d(TAG, "Successfully received " + cachedZones.size + " zones (" + cachedZones + ") from Home Assistant")
                cachedZones
            } catch (e: Exception) {
                Log.e(TAG, "Error receiving zones from Home Assistant", e)
                emptyList()
            }
        }
    }

    init {
        viewModelScope.launch {
            sensorFlow?.collect {
                sensor.value = it
            }
            sensorSettingsFlow.collect {
                sensorSettings.clear()
                sensorSettings.addAll(it)
            }
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        if (isEnabled) {
            sensorManager?.requiredPermissions(sensorId)?.let { permissions ->
                val fineLocation = DisabledLocationHandler.containsLocationPermission(permissions, true)
                val coarseLocation = DisabledLocationHandler.containsLocationPermission(permissions, false)

                if ((fineLocation || coarseLocation) &&
                    !DisabledLocationHandler.isLocationEnabled(app.applicationContext)
                ) {
                    locationPermissionRequests.value = LocationPermissionsDialog(block = true, sensors = arrayOf(basicSensor?.let { app.getString(basicSensor.name) } ?: ""))
                    return
                } else {
                    if (!sensorManager.checkPermission(app.applicationContext, sensorId)) {
                        if (sensorManager is NetworkSensorManager) {
                            locationPermissionRequests.value = LocationPermissionsDialog(block = false, sensors = emptyArray(), permissions = permissions)
                        } else if (sensorManager is LastAppSensorManager && !sensorManager.checkUsageStatsPermission(app.applicationContext)) {
                            permissionRequests.value = permissions
                        } else {
                            permissionRequests.value = permissions
                        }

                        return
                    }
                }
            } ?: return
        }

        updateSensorEntity(isEnabled)
        if (isEnabled) sensorManager?.requestSensorUpdate(app)
    }

    private fun updateSensorEntity(isEnabled: Boolean) {
        sensor.value?.let {
            sensorDao.update(
                it.sensor.copy().apply {
                    enabled = isEnabled
                    lastSentState = ""
                }
            )
        } ?: run {
            val sensorEntity = Sensor(sensorId, isEnabled, false, "")
            sensorDao.add(sensorEntity)
        }
        refreshSensorData()
    }

    private fun refreshSensorData() {
        SensorWorker.start(app.applicationContext)
    }

    fun onActivityResult() {
        // This is only called when we requested permissions to enable a sensor, so check if
        // we have all permissions and should enable the sensor.
        updateSensorEntity(sensorManager?.checkPermission(app, sensorId) == true)
        permissionRequests.value = emptyArray()
    }

    fun onPermissionsResult(results: Map<String, Boolean>) {
        // This is only called when we requested permissions to enable a sensor, so check if we
        // need to do another request, or if we have all permissions and should enable the sensor.
        if (results.keys.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        ) {
            permissionRequests.value = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }

        updateSensorEntity(results.values.all { it } && sensorManager?.checkPermission(app, sensorId) == true)
        permissionRequests.value = emptyArray()
    }
}
