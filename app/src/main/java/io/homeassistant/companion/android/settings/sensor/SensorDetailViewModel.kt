package io.homeassistant.companion.android.settings.sensor

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils
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

        private const val SENSOR_SETTING_TRANS_KEY_PREFIX = "sensor_setting_"

        data class LocationPermissionsDialog(
            val block: Boolean,
            val sensors: Array<String>,
            val permissions: Array<String>? = null
        )
        data class SettingDialogState(
            val setting: SensorSetting,
            val entries: List<String>? = null,
            val entriesIds: List<String>? = null,
            val entriesSelected: List<String>? = null
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
    private var sensorCheckedEnabled = false
    private val sensorSettingsFlow = sensorDao.getSettingsFlow(sensorId)
    var sensorSettings = mutableStateListOf<SensorSetting>()
        private set
    var sensorSettingsDialog = mutableStateOf<SettingDialogState?>(null)
        private set

    private val zones by lazy {
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
                if (!sensorCheckedEnabled) checkSensorEnabled()
            }
        }
        viewModelScope.launch {
            sensorSettingsFlow.collect {
                sensorSettings.clear()
                sensorSettings.addAll(it)
            }
        }
    }

    private fun checkSensorEnabled() {
        if (sensorManager != null && basicSensor != null) {
            sensor.value?.let {
                sensorCheckedEnabled = true
                val hasPermission = sensorManager.checkPermission(app.applicationContext, basicSensor.id)
                val enabled = it.sensor.enabled && hasPermission
                updateSensorEntity(enabled)
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
        if (isEnabled) try {
            sensorManager?.requestSensorUpdate(app)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while requesting update for sensor $sensorId", e)
        }
    }

    fun onSettingWithDialogPressed(setting: SensorSetting) {
        val listSetting = setting.valueType != "string" && setting.valueType != "number"
        val listEntries = getSettingEntries(setting)
        val state = SettingDialogState(
            setting = setting,
            entries = if (listSetting) listEntries else null,
            entriesIds = if (listSetting) {
                if (setting.valueType == "list") setting.entries
                else listEntries
            } else {
                null
            },
            entriesSelected = if (listSetting) {
                setting.value.split(", ").filter {
                    if (setting.valueType == "list") setting.entries.contains(it)
                    else listEntries.contains(it)
                }
            } else {
                null
            }
        )
        sensorSettingsDialog.value = state
    }

    fun cancelSettingWithDialog() {
        sensorSettingsDialog.value = null
    }

    fun submitSettingWithDialog(data: SettingDialogState?) {
        if (data != null) {
            setSetting(data.setting)
        }
        sensorSettingsDialog.value = null
    }

    fun setSetting(setting: SensorSetting) {
        sensorDao.add(setting)
        try {
            sensorManager?.requestSensorUpdate(app)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while requesting update for sensor $sensorId", e)
        }
        refreshSensorData()
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

    fun getSettingTranslatedTitle(key: String): String {
        val name = SENSOR_SETTING_TRANS_KEY_PREFIX + getCleanedKey(key) + "_title"
        return getStringFromIdentifierString(key, name) ?: key
    }

    fun getSettingTranslatedEntry(key: String, entry: String): String {
        val name = SENSOR_SETTING_TRANS_KEY_PREFIX + getCleanedKey(key) + "_" + entry + "_label"
        return getStringFromIdentifierString(entry, name) ?: entry
    }

    private fun getSettingTranslatedEntries(key: String, entries: List<String>): List<String> {
        val translatedEntries = ArrayList<String>(entries.size)
        for (entry in entries) {
            val name = SENSOR_SETTING_TRANS_KEY_PREFIX + getCleanedKey(key) + "_" + entry + "_label"
            translatedEntries.add(getStringFromIdentifierString(entry, name) ?: entry)
        }
        return translatedEntries
    }

    private fun getStringFromIdentifierString(key: String, identifierString: String): String? {
        val rawVars = getRawVars(key)
        val stringId = app.resources.getIdentifier(identifierString, "string", app.packageName)
        if (stringId != 0) {
            try {
                return app.getString(stringId, *convertRawVarsToStringVars(rawVars))
            } catch (e: Exception) {
                Log.w(TAG, "getStringFromIdentifierString: Cannot get translated string for name \"$identifierString\"", e)
            }
        } else {
            Log.e(TAG, "getStringFromIdentifierString: Cannot find string identifier for name \"$identifierString\"")
        }
        return null
    }

    private fun getCleanedKey(key: String): String {
        val varWithUnderscoreRegex = "_var\\d:.*:".toRegex()
        val cleanedKey = key.replace(varWithUnderscoreRegex, "")
        if (key != cleanedKey) Log.d(TAG, "Cleaned translation key \"$cleanedKey\"")
        return cleanedKey
    }

    private fun getRawVars(key: String): List<String> {
        val varRegex = "var\\d:.*:".toRegex()
        val rawVars = key.split("_").filter { it.matches(varRegex) }
        if (rawVars.isNotEmpty()) Log.d(TAG, "Vars from translation key \"$key\": $rawVars")
        return rawVars
    }

    private fun convertRawVarsToStringVars(rawVars: List<String>): Array<String> {
        var stringVars: MutableList<String> = ArrayList()
        if (rawVars.isNotEmpty()) {
            Log.d(TAG, "Convert raw vars \"$rawVars\" to string vars...")
            var varPrefixRegex = "var\\d:".toRegex()
            var varSuffixRegex = ":$".toRegex()
            for (rawVar in rawVars) {
                var stringVar = rawVar.replace(varPrefixRegex, "").replace(varSuffixRegex, "")
                Log.d(TAG, "Convert raw var \"$rawVar\" to string var \"$stringVar\"")
                stringVars.add(stringVar)
            }
            Log.d(TAG, "Converted raw vars to string vars \"$stringVars\"")
        }
        return stringVars.toTypedArray()
    }

    private fun getSettingEntries(setting: SensorSetting): List<String> {
        return when (setting.valueType) {
            "list" ->
                getSettingTranslatedEntries(setting.name, setting.entries)
            "list-apps" -> {
                val packageNames = mutableListOf<String>()
                app.packageManager?.getInstalledApplications(PackageManager.GET_META_DATA)?.let {
                    for (packageItem in it) {
                        packageNames.add(packageItem.packageName)
                    }
                    packageNames.sort()
                }
                return packageNames
            }
            "list-bluetooth" ->
                BluetoothUtils.getBluetoothDevices(app.applicationContext).map { it.name }
            "list-zones" ->
                zones
            else ->
                emptyList()
        }
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
