package io.homeassistant.companion.android.settings.sensor

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.sensors.NetworkSensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.sensor.SensorWithAttributes
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.sensors.LastAppSensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.SensorWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@HiltViewModel
class SensorDetailViewModel @Inject constructor(
    state: SavedStateHandle,
    private val integrationUseCase: IntegrationRepository,
    private val sensorDao: SensorDao,
    private val settingsDao: SettingsDao,
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
        data class PermissionSnackbar(
            @StringRes val message: Int,
            val actionOpensSettings: Boolean
        )
        data class SettingDialogState(
            val setting: SensorSetting,
            /** List of entity ID to entity pairs */
            val entries: List<Pair<String, String>>,
            /** List of selected entity ID */
            val entriesSelected: List<String>
        )
    }

    val sensorId: String = state["id"]!!

    val permissionRequests = MutableLiveData<Array<String>>()
    val locationPermissionRequests = MutableLiveData<LocationPermissionsDialog?>()

    private val _permissionSnackbar = MutableSharedFlow<PermissionSnackbar>()
    var permissionSnackbar = _permissionSnackbar.asSharedFlow()

    var sensorManager: SensorManager? = runBlocking {
        SensorReceiver.MANAGERS
            .find {
                it.getAvailableSensors(getApplication(), null).any { sensor -> sensor.id == sensorId }
            }
    }
    var basicSensor: SensorManager.BasicSensor? = getBasicSensors()

    var sensor by mutableStateOf<SensorWithAttributes?>(null)
        private set
    private var sensorCheckedEnabled = false
    val sensorSettings = sensorDao.getSettingsFlow(sensorId).collectAsState()
    var sensorSettingsDialog by mutableStateOf<SettingDialogState?>(null)
        private set

    val settingUpdateFrequency by lazy {
        settingsDao.get(0)?.sensorUpdateFrequency ?: SensorUpdateFrequencySetting.NORMAL
    }

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
        val sensorFlow = sensorDao.getFullFlow(sensorId)
        viewModelScope.launch {
            sensorFlow.collect {
                sensor = it
                if (!sensorCheckedEnabled) checkSensorEnabled(it)
            }
        }
    }

    private fun getBasicSensors(): SensorManager.BasicSensor? {
        viewModelScope.launch {
            basicSensor = sensorManager?.getAvailableSensors(getApplication(), null)
                ?.find { it.id == sensorId }
        }
        return basicSensor
    }
    private suspend fun checkSensorEnabled(sensor: SensorWithAttributes?) {
        if (sensorManager != null && basicSensor != null && sensor != null) {
            sensorCheckedEnabled = true
            val hasPermission = sensorManager!!.checkPermission(getApplication(), basicSensor!!.id)
            val enabled = sensor.sensor.enabled && hasPermission
            updateSensorEntity(enabled)
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        if (isEnabled) {
            sensorManager?.requiredPermissions(sensorId)?.let { permissions ->
                val fineLocation = DisabledLocationHandler.containsLocationPermission(permissions, true)
                val coarseLocation = DisabledLocationHandler.containsLocationPermission(permissions, false)

                if ((fineLocation || coarseLocation) &&
                    !DisabledLocationHandler.isLocationEnabled(getApplication())
                ) {
                    val sensorName = basicSensor?.let { getApplication<Application>().getString(basicSensor!!.name) }.orEmpty()
                    locationPermissionRequests.value = LocationPermissionsDialog(block = true, sensors = arrayOf(sensorName))
                    return
                } else {
                    if (!sensorManager!!.checkPermission(getApplication(), sensorId)) {
                        if (sensorManager is NetworkSensorManager) {
                            locationPermissionRequests.value = LocationPermissionsDialog(block = false, sensors = emptyArray(), permissions = permissions)
                        } else if (sensorManager is LastAppSensorManager && !sensorManager!!.checkUsageStatsPermission(getApplication())) {
                            permissionRequests.value = permissions
                        } else {
                            permissionRequests.value = permissions
                        }

                        return
                    }
                }
            } ?: return
        }

        viewModelScope.launch {
            updateSensorEntity(isEnabled)
            if (isEnabled) try {
                sensorManager?.requestSensorUpdate(getApplication())
            } catch (e: Exception) {
                Log.e(TAG, "Exception while requesting update for sensor $sensorId", e)
            }
        }
    }

    /**
     * Builds a SettingDialogState based on the given Sensor Setting.
     * Should trigger a dialog open in view.
     */
    fun onSettingWithDialogPressed(setting: SensorSetting) {
        val listKeys = getSettingKeys(setting)
        val listEntries = getSettingEntries(setting, null)
        val state = SettingDialogState(
            setting = setting,
            entries = when {
                setting.valueType == SensorSettingType.LIST ||
                    setting.valueType == SensorSettingType.LIST_BLUETOOTH ->
                    listKeys.zip(listEntries)
                setting.valueType.listType ->
                    listEntries.map { it to it }
                else ->
                    emptyList()
            },
            entriesSelected = when {
                setting.valueType == SensorSettingType.LIST ||
                    setting.valueType == SensorSettingType.LIST_BLUETOOTH ->
                    setting.value.split(", ").filter { listKeys.contains(it) }
                setting.valueType.listType ->
                    setting.value.split(", ").filter { listEntries.contains(it) }
                else ->
                    emptyList()
            }
        )
        sensorSettingsDialog = state
    }

    fun cancelSettingWithDialog() {
        sensorSettingsDialog = null
    }

    fun submitSettingWithDialog(data: SettingDialogState?) {
        if (data != null) {
            setSetting(data.setting)
        }
        sensorSettingsDialog = null
    }

    fun setSetting(setting: SensorSetting) {
        sensorDao.add(setting)
        try {
            sensorManager?.requestSensorUpdate(getApplication())
        } catch (e: Exception) {
            Log.e(TAG, "Exception while requesting update for sensor $sensorId", e)
        }
        refreshSensorData()
    }

    private suspend fun updateSensorEntity(isEnabled: Boolean) {
        sensorDao.setSensorsEnabled(listOf(sensorId), isEnabled)
        refreshSensorData()
    }

    private fun refreshSensorData() {
        SensorWorker.start(getApplication())
    }

    fun getSettingTranslatedTitle(key: String): String {
        val name = SENSOR_SETTING_TRANS_KEY_PREFIX + getCleanedKey(key) + "_title"
        return getStringFromIdentifierString(key, name) ?: key
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
        val app = getApplication<Application>()
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
        val stringVars: MutableList<String> = ArrayList()
        if (rawVars.isNotEmpty()) {
            Log.d(TAG, "Convert raw vars \"$rawVars\" to string vars...")
            val varPrefixRegex = "var\\d:".toRegex()
            val varSuffixRegex = ":$".toRegex()
            for (rawVar in rawVars) {
                val stringVar = rawVar.replace(varPrefixRegex, "").replace(varSuffixRegex, "")
                Log.d(TAG, "Convert raw var \"$rawVar\" to string var \"$stringVar\"")
                stringVars.add(stringVar)
            }
            Log.d(TAG, "Converted raw vars to string vars \"$stringVars\"")
        }
        return stringVars.toTypedArray()
    }

    private fun getSettingKeys(setting: SensorSetting): List<String> {
        return when (setting.valueType) {
            SensorSettingType.LIST ->
                setting.entries
            SensorSettingType.LIST_BLUETOOTH ->
                BluetoothUtils.getBluetoothDevices(getApplication()).map { it.address }
            else ->
                emptyList()
        }
    }

    /**
     * Returns a list of user-friendly labels for setting entries.
     *
     * @param setting The setting for which to return strings.
     * @param entries The entries for which strings have to be returned. If set to null, strings
     * for all entries are returned.
     */
    fun getSettingEntries(setting: SensorSetting, entries: List<String>?): List<String> {
        return when (setting.valueType) {
            SensorSettingType.LIST ->
                getSettingTranslatedEntries(setting.name, entries ?: setting.entries)
            SensorSettingType.LIST_APPS ->
                entries ?: getApplication<Application>().packageManager
                    ?.getInstalledApplications(PackageManager.GET_META_DATA)
                    ?.map { packageItem -> packageItem.packageName }
                    ?.sorted()
                    .orEmpty()
            SensorSettingType.LIST_BLUETOOTH -> {
                val devices = BluetoothUtils.getBluetoothDevices(getApplication())
                    .filter { entries == null || entries.contains(it.address) }
                val entriesNotInDevices = entries
                    ?.filter { entry -> !devices.any { it.address == entry } }
                    .orEmpty()
                devices.map { it.name }.plus(entriesNotInDevices)
            }
            SensorSettingType.LIST_ZONES ->
                entries ?: zones
            else ->
                emptyList()
        }
    }

    fun onActivityResult() {
        viewModelScope.launch {
            // This is only called when we requested permissions to enable a sensor, so check if
            // we have all permissions and should enable the sensor.
            val hasPermission = sensorManager?.checkPermission(getApplication(), sensorId) == true
            if (!hasPermission) {
                _permissionSnackbar.emit(PermissionSnackbar(commonR.string.enable_sensor_missing_permission_general, false))
            }
            updateSensorEntity(hasPermission)
            permissionRequests.value = emptyArray()
        }
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

        viewModelScope.launch {
            val hasPermission = results.values.all { it } && sensorManager?.checkPermission(getApplication(), sensorId) == true
            if (!hasPermission) {
                _permissionSnackbar.emit(
                    PermissionSnackbar(
                        when (results.entries.firstOrNull { !it.value }?.key) {
                            Manifest.permission.ACTIVITY_RECOGNITION ->
                                commonR.string.enable_sensor_missing_permission_activity_recognition
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION ->
                                commonR.string.enable_sensor_missing_permission_location
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                            Manifest.permission.BLUETOOTH_CONNECT ->
                                commonR.string.enable_sensor_missing_permission_nearby_devices
                            Manifest.permission.READ_PHONE_STATE ->
                                commonR.string.enable_sensor_missing_permission_phone
                            else ->
                                commonR.string.enable_sensor_missing_permission_general
                        },
                        true
                    )
                )
            }
            updateSensorEntity(hasPermission)
            permissionRequests.value = emptyArray()
        }
    }

    /**
     * Convert a Flow into a State object that updates until the view model is cleared.
     */
    private fun <T> Flow<T>.collectAsState(
        initial: T
    ): State<T> {
        val state = mutableStateOf(initial)
        viewModelScope.launch {
            collect { state.value = it }
        }
        return state
    }
    private fun <T> Flow<List<T>>.collectAsState(): State<List<T>> = collectAsState(initial = emptyList())
}
