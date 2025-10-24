package io.homeassistant.companion.android.settings.sensor

import android.Manifest
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.BluetoothSensorManager
import io.homeassistant.companion.android.common.sensors.NetworkSensorManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.sensor.SensorWithAttributes
import io.homeassistant.companion.android.database.sensor.toSensorsWithAttributes
import io.homeassistant.companion.android.database.settings.SensorUpdateFrequencySetting
import io.homeassistant.companion.android.database.settings.SettingsDao
import io.homeassistant.companion.android.sensors.LastAppSensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@HiltViewModel
class SensorDetailViewModel @Inject constructor(
    state: SavedStateHandle,
    private val serverManager: ServerManager,
    private val sensorDao: SensorDao,
    private val settingsDao: SettingsDao,
    private val prefsRepository: PrefsRepository,
    application: Application,
) : AndroidViewModel(application) {

    companion object {
        private const val SENSOR_SETTING_TRANS_KEY_PREFIX = "sensor_setting_"

        data class PermissionsDialog(val serverId: Int?, val permissions: Array<String>? = null)
        data class LocationPermissionsDialog(
            val block: Boolean,
            val serverId: Int?,
            val sensors: Array<String>,
            val permissions: Array<String>? = null,
        )
        data class PermissionSnackbar(
            @StringRes val message: Int,
            val actionOpensSettings: Boolean,
            val serverId: Int? = null,
        )
        data class SettingDialogState(
            val setting: SensorSetting,
            /** Indicates if this is still loading entries in the background */
            val loading: Boolean,
            /** List of entity ID to entity pairs */
            val entries: List<Pair<String, String>>,
            /** List of selected entity ID */
            val entriesSelected: List<String>,
        )
    }

    val sensorId: String = state["id"]!!

    val permissionRequests = MutableLiveData<PermissionsDialog?>()
    val locationPermissionRequests = MutableLiveData<LocationPermissionsDialog?>()

    private val _permissionSnackbar = MutableSharedFlow<PermissionSnackbar>()
    var permissionSnackbar = _permissionSnackbar.asSharedFlow()

    val sensorManager: SensorManager? = runBlocking {
        SensorReceiver.MANAGERS
            .find {
                it.getAvailableSensors(getApplication()).any { sensor -> sensor.id == sensorId }
            }
    }

    val basicSensor: SensorManager.BasicSensor? = runBlocking {
        sensorManager?.getAvailableSensors(getApplication())
            ?.find { it.id == sensorId }
    }

    /** A list of all sensors (for each server) with states */
    var sensors by mutableStateOf<List<SensorWithAttributes>>(emptyList())
        private set

    /** A sensor for displaying the main state in the UI */
    var sensor by mutableStateOf<SensorWithAttributes?>(null)
        private set
    private var sensorCheckedEnabled = false
    val sensorSettings = sensorDao.getSettingsFlow(sensorId).collectAsState()
    var sensorSettingsDialog by mutableStateOf<SettingDialogState?>(null)
        private set

    var settingUpdateFrequency by mutableStateOf<SensorUpdateFrequencySetting>(SensorUpdateFrequencySetting.NORMAL)
        private set

    val serverNames: Map<Int, String>
        get() = serverManager.defaultServers.associate { it.id to it.friendlyName }

    private val _serversShowExpand = MutableStateFlow(false)
    val serversShowExpand = _serversShowExpand.asStateFlow()
    private val _serversDoExpand = MutableStateFlow(false)

    private val _showPrivacyHint = MutableStateFlow(false)
    val serversDoExpand = _serversDoExpand.asStateFlow()
    val serversStateExpand = serversDoExpand.collectAsState(false)

    val showPrivacyHint = _showPrivacyHint.asStateFlow()

    private val zones by lazy {
        Timber.d("Get zones from Home Assistant for listing zones in preferences...")
        runBlocking {
            val cachedZones = mutableListOf<String>()
            serverManager.defaultServers.map { server ->
                async {
                    try {
                        serverManager.integrationRepository(server.id).getZones().map { "${server.id}_${it.entityId}" }
                    } catch (e: Exception) {
                        Timber.e(e, "Error receiving zones from Home Assistant")
                        emptyList()
                    }
                }
            }.awaitAll().forEach { cachedZones.addAll(it) }
            Timber.d("Successfully received " + cachedZones.size + " zones (" + cachedZones + ") from Home Assistant")
            cachedZones
        }
    }

    init {
        val sensorFlow = sensorDao.getFullFlow(sensorId)
        viewModelScope.launch {
            sensorFlow.collect { map ->
                sensors = map.toSensorsWithAttributes()
                sensor = map.toSensorsWithAttributes().maxByOrNull { it.sensor.enabled }
                if (!sensorCheckedEnabled) checkSensorEnabled(sensors)

                val expandable =
                    sensors.size > 1 && (sensors.all { it.sensor.enabled } || sensors.all { !it.sensor.enabled })
                _serversShowExpand.emit(expandable)
                if (!expandable) {
                    if (sensors.size == 1) {
                        _serversDoExpand.emit(false)
                    } else {
                        _serversDoExpand.emit(true)
                    }
                }
            }
        }
        viewModelScope.launch {
            // 0 is used for storing app level settings
            settingUpdateFrequency = settingsDao.get(0)?.sensorUpdateFrequency ?: SensorUpdateFrequencySetting.NORMAL
        }
        viewModelScope.launch {
            _showPrivacyHint.update { prefsRepository.showPrivacyHint() }
        }
    }

    private suspend fun checkSensorEnabled(sensors: List<SensorWithAttributes>) {
        if (sensorManager != null && basicSensor != null && sensors.isNotEmpty()) {
            sensorCheckedEnabled = true
            val hasPermission = sensorManager.checkPermission(getApplication(), basicSensor.id)
            sensors.forEach { thisSensor ->
                val enabled = thisSensor.sensor.enabled && hasPermission
                updateSensorEntity(enabled, thisSensor.sensor.serverId)
            }
        }
    }

    fun setEnabled(isEnabled: Boolean, serverId: Int?) {
        viewModelScope.launch {
            if (isEnabled) {
                sensorManager?.requiredPermissions(getApplication(), sensorId)?.let { permissions ->
                    val fineLocation = DisabledLocationHandler.containsLocationPermission(permissions, true)
                    val coarseLocation = DisabledLocationHandler.containsLocationPermission(permissions, false)

                    if ((fineLocation || coarseLocation) &&
                        !DisabledLocationHandler.isLocationEnabled(getApplication())
                    ) {
                        val sensorName = basicSensor?.let {
                            getApplication<Application>().getString(
                                basicSensor.name,
                            )
                        }.orEmpty()
                        locationPermissionRequests.value =
                            LocationPermissionsDialog(block = true, serverId = serverId, sensors = arrayOf(sensorName))
                        return@launch
                    } else {
                        if (!sensorManager.checkPermission(getApplication(), sensorId)) {
                            if (sensorManager is NetworkSensorManager) {
                                locationPermissionRequests.value =
                                    LocationPermissionsDialog(false, serverId, emptyArray(), permissions)
                            } else if (sensorManager is LastAppSensorManager &&
                                !sensorManager.checkUsageStatsPermission(getApplication())
                            ) {
                                permissionRequests.value = PermissionsDialog(serverId, permissions)
                            } else {
                                permissionRequests.value = PermissionsDialog(serverId, permissions)
                            }

                            return@launch
                        }
                    }
                } ?: return@launch
            }

            updateSensorEntity(isEnabled, serverId)
            if (isEnabled) {
                try {
                    sensorManager?.requestSensorUpdate(getApplication())
                } catch (e: Exception) {
                    Timber.e(e, "Exception while requesting update for sensor $sensorId")
                }
            }
        }
    }

    fun setServersExpanded(expand: Boolean) = viewModelScope.launch { _serversDoExpand.emit(expand) }

    /**
     * Builds a SettingDialogState based on the given Sensor Setting. Depending on the
     * device and/or connection, this may take some time.
     * Should trigger a dialog open in view.
     */
    fun onSettingWithDialogPressed(setting: SensorSetting) = viewModelScope.launch {
        val dialogLoadingJob = launch {
            // In case getting entries takes too long, display a temporary loading dialog
            delay(1000L)
            sensorSettingsDialog = SettingDialogState(
                setting = setting,
                loading = true,
                entries = listOf(),
                entriesSelected = listOf(),
            )
        }

        val listKeys = getSettingKeys(setting)
        val listEntries = getSettingEntries(setting, null)
        val state = SettingDialogState(
            setting = setting,
            loading = false,
            entries = when {
                setting.valueType == SensorSettingType.LIST ||
                    setting.valueType == SensorSettingType.LIST_APPS ||
                    setting.valueType == SensorSettingType.LIST_BLUETOOTH ||
                    setting.valueType == SensorSettingType.LIST_ZONES ->
                    listKeys.zip(listEntries)
                setting.valueType.listType ->
                    listEntries.map { it to it }
                else ->
                    emptyList()
            },
            entriesSelected = when {
                setting.valueType == SensorSettingType.LIST ||
                    setting.valueType == SensorSettingType.LIST_APPS ||
                    setting.valueType == SensorSettingType.LIST_BLUETOOTH ||
                    setting.valueType == SensorSettingType.LIST_ZONES ->
                    setting.value.split(", ").filter { listKeys.contains(it) }
                setting.valueType.listType ->
                    setting.value.split(", ").filter { listEntries.contains(it) }
                else ->
                    emptyList()
            },
        )
        dialogLoadingJob.cancel()
        sensorSettingsDialog = state
    }

    fun cancelSettingWithDialog() {
        sensorSettingsDialog = null
    }

    fun discardShowPrivacyHint() {
        _showPrivacyHint.update { false }
        viewModelScope.launch {
            // 0 is used for storing app level settings
            prefsRepository.setShowPrivacyHint(false)
        }
    }

    fun submitSettingWithDialog(data: SettingDialogState?) {
        if (data != null) {
            setSetting(data.setting)
        }
        sensorSettingsDialog = null
    }

    fun setSetting(setting: SensorSetting) {
        viewModelScope.launch {
            sensorDao.add(setting)
            try {
                sensorManager?.requestSensorUpdate(getApplication())
            } catch (e: Exception) {
                Timber.e(e, "Exception while requesting update for sensor $sensorId")
            }
            refreshSensorData()
        }
    }

    private suspend fun updateSensorEntity(isEnabled: Boolean, serverId: Int?) {
        val serverIds =
            if (serverId == null) {
                serverManager.defaultServers.map { it.id }
            } else {
                listOf(serverId)
            }
        sensorDao.setSensorEnabled(sensorId, serverIds, isEnabled)
        refreshSensorData()
    }

    private fun refreshSensorData() {
        SensorReceiver.updateAllSensors(getApplication())
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
                Timber.w(
                    e,
                    "getStringFromIdentifierString: Cannot get translated string for name \"$identifierString\"",
                )
            }
        } else {
            Timber.e("getStringFromIdentifierString: Cannot find string identifier for name \"$identifierString\"")
        }
        return null
    }

    private fun getCleanedKey(key: String): String {
        val varWithUnderscoreRegex = "_var\\d:.*:".toRegex()
        val cleanedKey = key.replace(varWithUnderscoreRegex, "")
        if (key != cleanedKey) Timber.d("Cleaned translation key \"$cleanedKey\"")
        return cleanedKey
    }

    private fun getRawVars(key: String): List<String> {
        val varRegex = "var\\d:.*:".toRegex()
        val rawVars = key.split("_").filter { it.matches(varRegex) }
        if (rawVars.isNotEmpty()) Timber.d("Vars from translation key \"$key\": $rawVars")
        return rawVars
    }

    private fun convertRawVarsToStringVars(rawVars: List<String>): Array<String> {
        val stringVars: MutableList<String> = ArrayList()
        if (rawVars.isNotEmpty()) {
            Timber.d("Convert raw vars \"$rawVars\" to string vars...")
            val varPrefixRegex = "var\\d:".toRegex()
            val varSuffixRegex = ":$".toRegex()
            for (rawVar in rawVars) {
                val stringVar = rawVar.replace(varPrefixRegex, "").replace(varSuffixRegex, "")
                Timber.d("Convert raw var \"$rawVar\" to string var \"$stringVar\"")
                stringVars.add(stringVar)
            }
            Timber.d("Converted raw vars to string vars \"$stringVars\"")
        }
        return stringVars.toTypedArray()
    }

    /** @return list of [ApplicationInfo] for the [entries] or all applications if `null`*/
    private fun getApplicationInfoForEntries(entries: List<String>?): List<ApplicationInfo?> {
        val packageManager = getApplication<Application>().packageManager
        return if (entries?.isNotEmpty() == true) {
            entries.map {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getApplicationInfo(it, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getApplicationInfo(it, 0)
                    }
                } catch (e: NameNotFoundException) {
                    null
                }
            }
        } else {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager?.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager?.getInstalledApplications(PackageManager.GET_META_DATA)
            }
            appInfo.orEmpty()
        }
    }

    private fun getSettingKeys(setting: SensorSetting): List<String> {
        return when (setting.valueType) {
            SensorSettingType.LIST ->
                setting.entries
            SensorSettingType.LIST_APPS -> {
                val packageManager = getApplication<Application>().packageManager
                getApplicationInfoForEntries(null)
                    .filterNotNull()
                    .sortedBy {
                        packageManager.getApplicationLabel(it).let { label ->
                            when {
                                label.isBlank() -> it.packageName
                                label != it.packageName -> "$label\n(${it.packageName}"
                                else -> label.toString()
                            }
                        }.lowercase()
                    }
                    .map { it.packageName }
            }
            SensorSettingType.LIST_BLUETOOTH ->
                BluetoothUtils.getBluetoothDevices(getApplication()).map { it.address }
            SensorSettingType.LIST_ZONES ->
                zones
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
            SensorSettingType.LIST_APPS -> {
                val packageManager = getApplication<Application>().packageManager
                val apps = getApplicationInfoForEntries(entries)
                apps
                    .mapIndexed { index, info ->
                        if (info == null) return@mapIndexed entries?.get(index) ?: ""
                        val label = packageManager.getApplicationLabel(info)
                        when {
                            label.isBlank() ->
                                info.packageName
                            label != info.packageName ->
                                if (entries?.isNotEmpty() == true) label.toString() else "$label\n(${info.packageName})"
                            else ->
                                label.toString()
                        }
                    }
                    .sortedBy { it.lowercase() }
            }
            SensorSettingType.LIST_BLUETOOTH -> {
                val devices = BluetoothUtils.getBluetoothDevices(getApplication())
                    .filter { entries == null || entries.contains(it.address) }
                val entriesNotInDevices = entries
                    ?.filter { entry -> !devices.any { it.address == entry } }
                    .orEmpty()
                devices.map { it.name }.plus(entriesNotInDevices)
            }
            SensorSettingType.LIST_ZONES -> {
                val servers = serverManager.defaultServers
                val zonesWithNames = zones
                    .filter { entries == null || entries.contains(it) }
                    .map {
                        val server = servers.first { s -> s.id == it.split("_")[0].toInt() }
                        val zone = it.split("_", limit = 2)[1]
                        if (servers.size > 1) "${server.friendlyName}: $zone" else zone
                    }
                val entriesNotInZones = entries
                    ?.filter { entry -> !zones.contains(entry) }
                    .orEmpty()
                zonesWithNames.plus(entriesNotInZones)
            }
            SensorSettingType.LIST_BEACONS -> {
                // show current beacons and also previously selected UUIDs
                entries ?: (sensorManager as BluetoothSensorManager).getBeaconUUIDs()
                    .plus(setting.value.split(", ").filter { it.isNotEmpty() })
                    .sorted()
                    .distinct()
            }
            else ->
                emptyList()
        }
    }

    fun onActivityResult(serverId: Int?) {
        viewModelScope.launch {
            // This is only called when we requested permissions to enable a sensor, so check if
            // we have all permissions and should enable the sensor.
            val hasPermission = sensorManager?.checkPermission(getApplication(), sensorId) == true
            if (!hasPermission) {
                _permissionSnackbar.emit(
                    PermissionSnackbar(commonR.string.enable_sensor_missing_permission_general, false),
                )
            }
            updateSensorEntity(hasPermission, serverId)
            permissionRequests.value = null
        }
    }

    fun onPermissionsResult(results: Map<String, Boolean>, serverId: Int?) {
        // This is only called when we requested permissions to enable a sensor, so check if we
        // need to do another request, or if we have all permissions and should enable the sensor.
        if (results.keys.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            permissionRequests.value =
                PermissionsDialog(serverId, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            return
        }

        viewModelScope.launch {
            val hasPermission =
                results.values.all { it } && sensorManager?.checkPermission(getApplication(), sensorId) == true
            if (!hasPermission) {
                _permissionSnackbar.emit(
                    PermissionSnackbar(
                        when (results.entries.firstOrNull { !it.value }?.key) {
                            Manifest.permission.ACTIVITY_RECOGNITION ->
                                commonR.string.enable_sensor_missing_permission_activity_recognition
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            ->
                                commonR.string.enable_sensor_missing_permission_location
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            ->
                                commonR.string.enable_sensor_missing_permission_nearby_devices
                            Manifest.permission.READ_PHONE_STATE ->
                                commonR.string.enable_sensor_missing_permission_phone
                            else ->
                                commonR.string.enable_sensor_missing_permission_general
                        },
                        true,
                        serverId,
                    ),
                )
            }
            updateSensorEntity(hasPermission, serverId)
            permissionRequests.value = null
        }
    }

    /**
     * Convert a Flow into a State object that updates until the view model is cleared.
     */
    private fun <T> Flow<T>.collectAsState(initial: T): State<T> {
        val state = mutableStateOf(initial)
        viewModelScope.launch {
            collect { state.value = it }
        }
        return state
    }
    private fun <T> Flow<List<T>>.collectAsState(): State<List<T>> = collectAsState(initial = emptyList())
}
