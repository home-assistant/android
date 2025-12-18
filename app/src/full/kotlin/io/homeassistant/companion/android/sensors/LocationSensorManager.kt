package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.UpdateLocation
import io.homeassistant.companion.android.common.data.integration.containsWithAccuracy
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.notifications.DeviceCommandData
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.location.LocationHistoryItem
import io.homeassistant.companion.android.database.location.LocationHistoryItemResult
import io.homeassistant.companion.android.database.location.LocationHistoryItemTrigger
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.sensor.toSensorWithAttributes
import io.homeassistant.companion.android.location.HighAccuracyLocationService
import io.homeassistant.companion.android.notifications.MessagingManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class LocationSensorManager :
    BroadcastReceiver(),
    SensorManager {

    companion object {
        private const val SETTING_SEND_LOCATION_AS = "location_send_as"
        private const val SETTING_ACCURACY = "location_minimum_accuracy"
        private const val SETTING_ACCURATE_UPDATE_TIME = "location_minimum_time_updates"
        private const val SETTING_INCLUDE_SENSOR_UPDATE = "location_include_sensor_update"
        private const val SETTING_HIGH_ACCURACY_MODE = "location_ham_enabled"
        private const val SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL = "location_ham_update_interval"
        private const val SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES = "location_ham_only_bt_dev"
        private const val SETTING_HIGH_ACCURACY_MODE_ZONE = "location_ham_only_enter_zone"
        private const val SETTING_HIGH_ACCURACY_BT_ZONE_COMBINED = "location_ham_zone_bt_combined"
        private const val SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE = "location_ham_trigger_range"

        private const val SEND_LOCATION_AS_EXACT = "exact"
        private const val SEND_LOCATION_AS_ZONE_ONLY = "zone_only"
        private const val DEFAULT_MINIMUM_ACCURACY = 200
        private const val DEFAULT_UPDATE_INTERVAL_HA_SECONDS = 5
        private const val DEFAULT_TRIGGER_RANGE_METERS = 300

        private const val DEFAULT_LOCATION_INTERVAL: Long = 60000
        private const val DEFAULT_LOCATION_FAST_INTERVAL: Long = 30000
        private const val DEFAULT_LOCATION_MAX_WAIT_TIME: Long = 200000

        private const val ZONE_NAME_NOT_HOME = "not_home"

        private const val HISTORY_DURATION = 60 * 60 * 48 * 1000L // 60(s) * 60(m) * 48(h) to millis

        const val ACTION_REQUEST_LOCATION_UPDATES =
            "io.homeassistant.companion.android.background.REQUEST_UPDATES"
        const val ACTION_REQUEST_ACCURATE_LOCATION_UPDATE =
            "io.homeassistant.companion.android.background.REQUEST_ACCURATE_UPDATE"
        const val ACTION_PROCESS_LOCATION =
            "io.homeassistant.companion.android.background.PROCESS_UPDATES"
        const val ACTION_PROCESS_HIGH_ACCURACY_LOCATION =
            "io.homeassistant.companion.android.background.PROCESS_HIGH_ACCURACY_UPDATES"
        const val ACTION_PROCESS_GEO =
            "io.homeassistant.companion.android.background.PROCESS_GEOFENCE"
        const val ACTION_FORCE_HIGH_ACCURACY =
            "io.homeassistant.companion.android.background.FORCE_HIGH_ACCURACY"

        val backgroundLocation = SensorManager.BasicSensor(
            "location_background",
            "",
            commonR.string.basic_sensor_name_location_background,
            commonR.string.sensor_description_location_background,
            "mdi:map-marker-multiple",
            updateType = SensorManager.BasicSensor.UpdateType.LOCATION,
        )
        val zoneLocation = SensorManager.BasicSensor(
            "zone_background",
            "",
            commonR.string.basic_sensor_name_location_zone,
            commonR.string.sensor_description_location_zone,
            "mdi:map-marker-radius",
            updateType = SensorManager.BasicSensor.UpdateType.LOCATION,
        )
        val singleAccurateLocation = SensorManager.BasicSensor(
            "accurate_location",
            "",
            commonR.string.basic_sensor_name_location_accurate,
            commonR.string.sensor_description_location_accurate,
            "mdi:crosshairs-gps",
            updateType = SensorManager.BasicSensor.UpdateType.LOCATION,
        )

        val highAccuracyMode = SensorManager.BasicSensor(
            "high_accuracy_mode",
            "binary_sensor",
            commonR.string.basic_sensor_name_high_accuracy_mode,
            commonR.string.sensor_description_high_accuracy_mode,
            "mdi:crosshairs-gps",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        val highAccuracyUpdateInterval = SensorManager.BasicSensor(
            "high_accuracy_update_interval",
            "sensor",
            commonR.string.basic_sensor_name_high_accuracy_interval,
            commonR.string.sensor_description_high_accuracy_interval,
            "mdi:timer",
            unitOfMeasurement = "seconds",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        private var geofencingClient: GeofencingClient? = null
        private var fusedLocationProviderClient: FusedLocationProviderClient? = null

        private var isBackgroundLocationSetup = false
        private var isZoneLocationSetup = false

        private var lastLocationSend = mutableMapOf<Int, Long>()
        private var lastLocationReceived = mutableMapOf<Int, Long>()
        private var lastUpdateLocation = mutableMapOf<Int, String?>()

        private var zones = mutableMapOf<Int, List<Entity>>()
        private var zonesLastReceived = mutableMapOf<Int, Long>()

        private var geofenceRegistered = mutableSetOf<Int>()

        private var lastHighAccuracyMode = false
        private var lastHighAccuracyUpdateInterval = DEFAULT_UPDATE_INTERVAL_HA_SECONDS
        private var forceHighAccuracyModeOn = false
        private var forceHighAccuracyModeOff = false
        private var highAccuracyModeEnabled = false

        private var lastEnteredGeoZones: MutableList<String> = ArrayList()
        private var lastExitedGeoZones: MutableList<String> = ArrayList()

        private var lastHighAccuracyTriggerRange: Int = 0
        private var lastHighAccuracyZones: List<String> = ArrayList()

        enum class LocationUpdateTrigger(val isGeofence: Boolean = false) {
            HIGH_ACCURACY_LOCATION,
            BACKGROUND_LOCATION,
            GEOFENCE_ENTER(isGeofence = true),
            GEOFENCE_EXIT(isGeofence = true),
            GEOFENCE_DWELL(isGeofence = true),
            SINGLE_ACCURATE_LOCATION,
        }

        suspend fun setHighAccuracyModeSetting(context: Context, enabled: Boolean) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            sensorDao.add(
                SensorSetting(
                    backgroundLocation.id,
                    SETTING_HIGH_ACCURACY_MODE,
                    enabled.toString(),
                    SensorSettingType.TOGGLE,
                ),
            )
        }

        suspend fun getHighAccuracyModeIntervalSetting(context: Context): Int {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            val sensorSettings = sensorDao.getSettings(backgroundLocation.id)
            return sensorSettings.firstOrNull {
                it.name == SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL
            }?.value?.toIntOrNull()
                ?: DEFAULT_UPDATE_INTERVAL_HA_SECONDS
        }

        suspend fun setHighAccuracyModeIntervalSetting(context: Context, updateInterval: Int) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            sensorDao.add(
                SensorSetting(
                    backgroundLocation.id,
                    SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL,
                    updateInterval.toString(),
                    SensorSettingType.NUMBER,
                ),
            )
        }
    }

    @Inject
    lateinit var prefsRepository: PrefsRepository

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    lateinit var latestContext: Context

    override fun onReceive(context: Context, intent: Intent) {
        latestContext = context

        sensorWorkerScope.launch {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED,
                ACTION_REQUEST_LOCATION_UPDATES,
                -> setupLocationTracking()

                ACTION_PROCESS_LOCATION,
                ACTION_PROCESS_HIGH_ACCURACY_LOCATION,
                -> handleLocationUpdate(intent)

                ACTION_PROCESS_GEO -> handleGeoUpdate(intent)
                ACTION_REQUEST_ACCURATE_LOCATION_UPDATE -> requestSingleAccurateLocation()
                ACTION_FORCE_HIGH_ACCURACY -> {
                    when (val command = intent.extras?.getString("command")) {
                        DeviceCommandData.TURN_ON, DeviceCommandData.TURN_OFF, MessagingManager.FORCE_ON -> {
                            val turnOn = command != DeviceCommandData.TURN_OFF
                            if (turnOn) {
                                Timber.d("Forcing of high accuracy mode enabled")
                            } else {
                                Timber.d("Forcing of high accuracy mode disabled")
                            }
                            forceHighAccuracyModeOn = turnOn
                            forceHighAccuracyModeOff = false
                            setHighAccuracyModeSetting(latestContext, turnOn)
                            setupBackgroundLocation()
                        }

                        MessagingManager.FORCE_OFF -> {
                            Timber.d("High accuracy mode forced off")
                            forceHighAccuracyModeOn = false
                            forceHighAccuracyModeOff = true
                            setupBackgroundLocation()
                        }

                        MessagingManager.HIGH_ACCURACY_SET_UPDATE_INTERVAL -> {
                            if (lastHighAccuracyMode) {
                                restartHighAccuracyService(getHighAccuracyModeIntervalSetting(latestContext))
                            }
                        }
                    }
                }

                else -> Timber.w("Unknown intent action: ${intent.action}!")
            }
        }
    }

    private suspend fun setupLocationTracking() {
        if (!checkPermission(latestContext, backgroundLocation.id)) {
            Timber.w("Not starting location reporting because of permissions.")
            return
        }

        val backgroundEnabled = isEnabled(latestContext, backgroundLocation)
        val zoneEnabled = isEnabled(latestContext, zoneLocation)
        val zoneServers = getEnabledServers(latestContext, zoneLocation)

        try {
            if (!backgroundEnabled && !zoneEnabled) {
                removeAllLocationUpdateRequests()
                isBackgroundLocationSetup = false
                isZoneLocationSetup = false
            }
            if (!zoneEnabled && isZoneLocationSetup) {
                removeGeofenceUpdateRequests()
                isZoneLocationSetup = false
            }
            if (!backgroundEnabled && isBackgroundLocationSetup) {
                removeBackgroundUpdateRequests()
                stopHighAccuracyService()
                isBackgroundLocationSetup = false
            }
            if (zoneEnabled && !isZoneLocationSetup) {
                isZoneLocationSetup = true
                requestZoneUpdates()
            }
            if (zoneEnabled && isZoneLocationSetup && geofenceRegistered != zoneServers) {
                Timber.d("Zone enabled servers changed. Reconfigure zones.")
                removeGeofenceUpdateRequests()
                requestZoneUpdates()
            }

            val now = System.currentTimeMillis()
            if (
                (!highAccuracyModeEnabled && isBackgroundLocationSetup) &&
                (lastLocationReceived.all { (it.value + (DEFAULT_LOCATION_MAX_WAIT_TIME * 2L)) < now })
            ) {
                Timber.d("Background location updates appear to have stopped, restarting location updates")
                isBackgroundLocationSetup = false
                fusedLocationProviderClient?.flushLocations()
                removeBackgroundUpdateRequests()
            } else if (
                highAccuracyModeEnabled &&
                (lastLocationReceived.all { (it.value + (getHighAccuracyModeUpdateInterval().toLong() * 2000L)) < now })
            ) {
                Timber.d("High accuracy mode appears to have stopped, restarting high accuracy mode")
                isBackgroundLocationSetup = false
                stopHighAccuracyService()
            }

            setupBackgroundLocation(backgroundEnabled, zoneEnabled)
        } catch (e: Exception) {
            Timber.e(e, "Issue setting up location tracking")
        }
    }

    private suspend fun setupBackgroundLocation(backgroundEnabled: Boolean? = null, zoneEnabled: Boolean? = null) {
        var isBackgroundEnabled = backgroundEnabled
        var isZoneEnable = zoneEnabled
        if (isBackgroundEnabled == null) isBackgroundEnabled = isEnabled(latestContext, backgroundLocation)
        if (isZoneEnable == null) isZoneEnable = isEnabled(latestContext, zoneLocation)

        if (isBackgroundEnabled) {
            val updateIntervalHighAccuracySeconds = getHighAccuracyModeUpdateInterval()
            highAccuracyModeEnabled = getHighAccuracyModeState()
            val highAccuracyTriggerRange = getHighAccuracyModeTriggerRange()
            val highAccuracyZones = getHighAccuracyModeZones(false)

            if (!isBackgroundLocationSetup) {
                isBackgroundLocationSetup = true
                if (highAccuracyModeEnabled) {
                    startHighAccuracyService(updateIntervalHighAccuracySeconds)
                } else {
                    requestLocationUpdates()
                }
            } else {
                if (highAccuracyModeEnabled != lastHighAccuracyMode ||
                    updateIntervalHighAccuracySeconds != lastHighAccuracyUpdateInterval
                ) {
                    if (highAccuracyModeEnabled) {
                        Timber.d("High accuracy mode parameters changed. Enable high accuracy mode.")
                        if (updateIntervalHighAccuracySeconds != lastHighAccuracyUpdateInterval) {
                            restartHighAccuracyService(updateIntervalHighAccuracySeconds)
                        } else {
                            removeBackgroundUpdateRequests()
                            startHighAccuracyService(updateIntervalHighAccuracySeconds)
                        }
                    } else {
                        Timber.d("High accuracy mode parameters changed. Disable high accuracy mode.")
                        stopHighAccuracyService()
                        requestLocationUpdates()
                    }
                }

                if (highAccuracyTriggerRange != lastHighAccuracyTriggerRange ||
                    highAccuracyZones != lastHighAccuracyZones
                ) {
                    Timber.d("High accuracy mode geo parameters changed. Reconfigure zones.")
                    removeGeofenceUpdateRequests()
                    requestZoneUpdates()
                }
            }

            val highAccuracyModeSettingEnabled = getHighAccuracyModeSetting()
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL,
                highAccuracyModeSettingEnabled,
            )
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES,
                highAccuracyModeSettingEnabled,
            )
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_MODE_ZONE,
                highAccuracyModeSettingEnabled && isZoneEnable,
            )
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE,
                highAccuracyModeSettingEnabled && isZoneEnable,
            )
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_BT_ZONE_COMBINED,
                highAccuracyModeSettingEnabled && isZoneEnable,
            )

            lastHighAccuracyZones = highAccuracyZones
            lastHighAccuracyTriggerRange = highAccuracyTriggerRange
            lastHighAccuracyMode = highAccuracyModeEnabled
            lastHighAccuracyUpdateInterval = updateIntervalHighAccuracySeconds

            serverManager(latestContext).defaultServers.forEach {
                getSendLocationAsSetting(it.id) // Sets up the setting, value isn't used right now
            }
        }
    }

    private suspend fun restartHighAccuracyService(intervalInSeconds: Int) {
        onSensorUpdated(
            latestContext,
            highAccuracyUpdateInterval,
            intervalInSeconds,
            highAccuracyUpdateInterval.statelessIcon,
            mapOf(),
        )
        SensorReceiver.updateAllSensors(latestContext)
        HighAccuracyLocationService.restartService(latestContext, intervalInSeconds)
    }

    private suspend fun startHighAccuracyService(intervalInSeconds: Int) {
        onSensorUpdated(
            latestContext,
            highAccuracyMode,
            true,
            highAccuracyMode.statelessIcon,
            mapOf(),
        )
        onSensorUpdated(
            latestContext,
            highAccuracyUpdateInterval,
            intervalInSeconds,
            highAccuracyUpdateInterval.statelessIcon,
            mapOf(),
        )
        SensorReceiver.updateAllSensors(latestContext)
        HighAccuracyLocationService.startService(latestContext, intervalInSeconds)
    }

    private suspend fun stopHighAccuracyService() {
        onSensorUpdated(
            latestContext,
            highAccuracyMode,
            false,
            highAccuracyMode.statelessIcon,
            mapOf(),
        )
        SensorReceiver.updateAllSensors(latestContext)
        HighAccuracyLocationService.stopService(latestContext)
    }

    private suspend fun getHighAccuracyModeUpdateInterval(): Int {
        val updateIntervalHighAccuracySeconds = getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL,
            SensorSettingType.NUMBER,
            DEFAULT_UPDATE_INTERVAL_HA_SECONDS.toString(),
        )

        var updateIntervalHighAccuracySecondsInt = if (updateIntervalHighAccuracySeconds.isEmpty()) {
            DEFAULT_UPDATE_INTERVAL_HA_SECONDS
        } else {
            updateIntervalHighAccuracySeconds.toInt()
        }
        if (updateIntervalHighAccuracySecondsInt < 5) {
            updateIntervalHighAccuracySecondsInt = DEFAULT_UPDATE_INTERVAL_HA_SECONDS

            setHighAccuracyModeIntervalSetting(latestContext, updateIntervalHighAccuracySecondsInt)
        }
        return updateIntervalHighAccuracySecondsInt
    }

    private suspend fun getHighAccuracyModeState(): Boolean {
        val highAccuracyMode = getHighAccuracyModeSetting()

        if (!highAccuracyMode) return false

        val shouldEnableHighAccuracyMode = shouldEnableHighAccuracyMode()

        // As soon as the high accuracy mode should be enabled, disable the force_on of high accuracy mode!
        if (shouldEnableHighAccuracyMode && forceHighAccuracyModeOn) {
            Timber.d("Forcing of high accuracy mode disabled, because high accuracy mode had to be enabled anyway.")
            forceHighAccuracyModeOn = false
        }

        // As soon as the high accuracy mode shouldn't be enabled, disable the force_off of high accuracy mode!
        if (!shouldEnableHighAccuracyMode && forceHighAccuracyModeOff) {
            Timber.d(
                "Forcing off of high accuracy mode disabled, because high accuracy mode had to be disabled anyway.",
            )
            forceHighAccuracyModeOff = false
        }

        return if (forceHighAccuracyModeOn) {
            Timber.d("High accuracy mode enabled, because command_high_accuracy_mode was used to turn it on")
            true
        } else if (forceHighAccuracyModeOff) {
            Timber.d("High accuracy mode disabled, because command_high_accuracy_mode was used to force it off")
            false
        } else {
            shouldEnableHighAccuracyMode
        }
    }

    private suspend fun shouldEnableHighAccuracyMode(): Boolean {
        val highAccuracyModeBTDevicesSetting = getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES,
            SensorSettingType.LIST_BLUETOOTH,
            "",
        )
        val highAccuracyModeBTDevices = highAccuracyModeBTDevicesSetting
            .split(", ")
            .mapNotNull { it.trim().ifBlank { null } }
            .toMutableList()
        val highAccuracyBtZoneCombined = getHighAccuracyBTZoneCombinedSetting()

        val useTriggerRange = getHighAccuracyModeTriggerRange() > 0
        val highAccuracyZones = getHighAccuracyModeZones(false)
        var highAccuracyExpZones = highAccuracyZones
        if (useTriggerRange) {
            // Use a trigger range, if defined
            highAccuracyExpZones = getHighAccuracyModeZones(true)
        }

        var btDevConnected = false
        var inZone = false
        var constraintsUsed = false

        if (highAccuracyModeBTDevices.isNotEmpty()) {
            constraintsUsed = true

            val bluetoothDevices = BluetoothUtils.getBluetoothDevices(latestContext)

            // If any of the stored devices aren't a Bluetooth device address, try to match them to a device
            var updatedBtDeviceNames = false
            highAccuracyModeBTDevices.filter { !BluetoothAdapter.checkBluetoothAddress(it) }.forEach {
                val foundDevices = bluetoothDevices.filter { btDevice -> btDevice.name == it }
                if (foundDevices.isNotEmpty()) {
                    highAccuracyModeBTDevices.remove(it)
                    foundDevices.forEach { btDevice ->
                        if (!highAccuracyModeBTDevices.contains(btDevice.address)) {
                            highAccuracyModeBTDevices.add(btDevice.address)
                        }
                    }
                    updatedBtDeviceNames = true
                }
            }
            if (updatedBtDeviceNames) {
                val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
                sensorDao.add(
                    SensorSetting(
                        backgroundLocation.id,
                        SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES,
                        highAccuracyModeBTDevices.joinToString().replace("[", "").replace("]", ""),
                        SensorSettingType.LIST_BLUETOOTH,
                    ),
                )
            }

            btDevConnected = bluetoothDevices.any { it.connected && highAccuracyModeBTDevices.contains(it.address) }

            if (!forceHighAccuracyModeOn && !forceHighAccuracyModeOff) {
                if (!btDevConnected) {
                    Timber.d(
                        "High accuracy mode disabled, because defined ($highAccuracyModeBTDevices) bluetooth device(s) not connected (Connected devices: $bluetoothDevices)",
                    )
                } else {
                    Timber.d(
                        "High accuracy mode enabled, because defined ($highAccuracyModeBTDevices) bluetooth device(s) connected (Connected devices: $bluetoothDevices)",
                    )
                }
            }
        }

        if (highAccuracyZones.isNotEmpty()) {
            constraintsUsed = true

            // (Expanded) Zone entered
            val zoneExpEntered =
                lastEnteredGeoZones.isNotEmpty() && highAccuracyExpZones.containsAll(lastEnteredGeoZones)

            // Exits events are only used if expended zones are used. The exit events are used to determine the enter of the expanded zone from the original zone
            // Zone exited
            val zoneExited =
                useTriggerRange && lastExitedGeoZones.isNotEmpty() && highAccuracyZones.containsAll(lastExitedGeoZones)

            inZone = zoneExpEntered || zoneExited

            if (!forceHighAccuracyModeOn && !forceHighAccuracyModeOff) {
                if (!inZone) {
                    Timber.d("High accuracy mode disabled, because not in zone $highAccuracyExpZones")
                } else {
                    Timber.d("High accuracy mode enabled, because in zone $highAccuracyExpZones")
                }
            }
        }

        // true = High accuracy mode enabled
        // false = High accuracy mode disabled
        //
        // if BT device and zone are combined and BT device is connected AND in zone -> High accuracy mode enabled (true)
        // if BT device and zone are NOT combined and either BT Device is connected OR in Zone -> High accuracy mode enabled (true)
        // Else (NO BT dev connected and NOT in Zone), if min. one constraint is used ->  High accuracy mode disabled (false)
        //                                             if no constraint is used ->  High accuracy mode enabled (true)
        return when {
            highAccuracyBtZoneCombined && btDevConnected && inZone -> true
            !highAccuracyBtZoneCombined && (btDevConnected || inZone) -> true
            highAccuracyBtZoneCombined && !constraintsUsed -> false
            else -> !constraintsUsed
        }
    }

    private suspend fun getHighAccuracyModeSetting(): Boolean {
        return getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE,
            SensorSettingType.TOGGLE,
            "false",
        ).toBoolean()
    }

    private suspend fun getHighAccuracyBTZoneCombinedSetting(): Boolean {
        return getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_BT_ZONE_COMBINED,
            SensorSettingType.TOGGLE,
            "false",
        ).toBoolean()
    }

    private suspend fun getSendLocationAsSetting(serverId: Int): String {
        return if (serverManager(latestContext).getServer(serverId)?.version?.isAtLeast(2022, 2, 0) == true) {
            getSetting(
                context = latestContext,
                sensor = backgroundLocation,
                settingName = SETTING_SEND_LOCATION_AS,
                settingType = SensorSettingType.LIST,
                entries = listOf(
                    SEND_LOCATION_AS_EXACT,
                    SEND_LOCATION_AS_ZONE_ONLY,
                ),
                default = SEND_LOCATION_AS_EXACT,
            )
        } else {
            SEND_LOCATION_AS_EXACT
        }
    }

    private fun removeAllLocationUpdateRequests() {
        Timber.d("Removing all location requests.")
        removeBackgroundUpdateRequests()
        removeGeofenceUpdateRequests()
    }

    private fun removeBackgroundUpdateRequests() {
        if (fusedLocationProviderClient != null) {
            Timber.d("Removing background location requests.")
            val backgroundIntent = getLocationUpdateIntent(false)
            fusedLocationProviderClient?.removeLocationUpdates(backgroundIntent)
        } else {
            Timber.d("Cannot remove background location requests. Location provider is not set.")
        }
    }

    private fun removeGeofenceUpdateRequests() {
        if (geofencingClient != null) {
            Timber.d("Removing geofence location requests.")
            val zoneIntent = getLocationUpdateIntent(true)
            geofencingClient?.removeGeofences(zoneIntent)
            geofenceRegistered.clear()
            lastEnteredGeoZones.clear()
            lastExitedGeoZones.clear()
        } else {
            Timber.d("Cannot remove geofence location requests. Geofence provider is not set.")
        }
    }

    private suspend fun requestLocationUpdates() {
        if (!checkPermission(latestContext, backgroundLocation.id)) {
            Timber.w("Not registering for location updates because of permissions.")
            return
        }
        Timber.d("Registering for location updates.")

        fusedLocationProviderClient = try {
            LocationServices.getFusedLocationProviderClient(latestContext)
        } catch (e: Exception) {
            Timber.e(e, "Unable to get fused location provider client")
            null
        }

        val intent = getLocationUpdateIntent(false)

        fusedLocationProviderClient?.requestLocationUpdates(
            createLocationRequest(),
            intent,
        )
    }

    private suspend fun requestZoneUpdates() {
        if (!checkPermission(latestContext, zoneLocation.id)) {
            Timber.w("Not registering for zone based updates because of permissions.")
            return
        }

        if (geofenceRegistered == getEnabledServers(latestContext, zoneLocation)) {
            Timber.w("Not registering for zones as we already have / haven't")
            return
        }

        Timber.d("Registering for zone based location updates")

        try {
            geofencingClient = LocationServices.getGeofencingClient(latestContext)
            val intent = getLocationUpdateIntent(true)
            val geofencingRequest = createGeofencingRequest()
            if (geofencingRequest != null) {
                geofencingClient?.addGeofences(
                    geofencingRequest,
                    intent,
                )
            } else {
                Timber.w("No zones, skipping zone based location updates")
            }
        } catch (e: Exception) {
            Timber.e(e, "Issue requesting zone updates.")
        }
    }

    private suspend fun handleLocationUpdate(intent: Intent) {
        Timber.d("Received location update.")
        val serverIds = getEnabledServers(latestContext, backgroundLocation)
        serverIds.forEach {
            lastLocationReceived[it] = System.currentTimeMillis()
        }
        LocationResult.extractResult(intent)?.lastLocation?.let { location ->
            val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
            val sensorSettings = sensorDao.getSettings(backgroundLocation.id)
            val minAccuracy = sensorSettings
                .firstOrNull { it.name == SETTING_ACCURACY }?.value?.toIntOrNull()
                ?: DEFAULT_MINIMUM_ACCURACY
            sensorDao.add(
                SensorSetting(
                    backgroundLocation.id,
                    SETTING_ACCURACY,
                    minAccuracy.toString(),
                    SensorSettingType.NUMBER,
                ),
            )
            val trigger =
                if (intent.action == ACTION_PROCESS_HIGH_ACCURACY_LOCATION) {
                    LocationUpdateTrigger.HIGH_ACCURACY_LOCATION
                } else {
                    LocationUpdateTrigger.BACKGROUND_LOCATION
                }
            if (location.accuracy > minAccuracy) {
                Timber.w("Location accuracy didn't meet requirements, disregarding: $location")
                logLocationUpdate(location, null, null, trigger, LocationHistoryItemResult.SKIPPED_ACCURACY)
            } else {
                HighAccuracyLocationService.updateNotificationAddress(latestContext, location)
                // Send new location to Home Assistant
                serverIds.forEach {
                    ioScope.launch { sendLocationUpdate(location, it, trigger) }
                }
            }
        }
    }

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (DisabledLocationHandler.hasGPS(context)) {
            listOf(
                singleAccurateLocation,
                backgroundLocation,
                zoneLocation,
                highAccuracyMode,
                highAccuracyUpdateInterval,
            )
        } else {
            listOf(backgroundLocation, zoneLocation, highAccuracyMode, highAccuracyUpdateInterval)
        }
    }

    private suspend fun handleGeoUpdate(intent: Intent) {
        Timber.d("Received geofence update.")
        if (!isEnabled(latestContext, zoneLocation)) {
            isZoneLocationSetup = false
            Timber.w("Unregistering geofences as zone tracking is disabled and intent was received")
            removeGeofenceUpdateRequests()
            return
        }
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent?.hasError() == true) {
            Timber.e("Error getting geofence broadcast status code: ${geofencingEvent.errorCode}")
            return
        }

        if (geofencingEvent?.triggeringLocation == null) {
            Timber.d("Geofence event is null")
            return
        }

        val validGeofencingEvents = listOf(Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_EXIT)
        if (geofencingEvent.geofenceTransition in validGeofencingEvents) {
            val zoneStatusEvent = when (geofencingEvent.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "android.zone_entered"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "android.zone_exited"
                else -> ""
            }

            for (triggeringGeofence in geofencingEvent.triggeringGeofences!!) {
                val zone = triggeringGeofence.requestId

                if (zoneStatusEvent == "android.zone_entered") {
                    lastEnteredGeoZones.add(zone)
                } else {
                    lastEnteredGeoZones.remove(zone)
                }

                if (zoneStatusEvent == "android.zone_exited") {
                    lastExitedGeoZones.add(zone)
                } else {
                    lastExitedGeoZones.remove(zone)
                }

                val zoneAttr = mapOf(
                    "accuracy" to geofencingEvent.triggeringLocation!!.accuracy,
                    "altitude" to geofencingEvent.triggeringLocation!!.altitude,
                    "bearing" to geofencingEvent.triggeringLocation!!.bearing,
                    "latitude" to geofencingEvent.triggeringLocation!!.latitude,
                    "longitude" to geofencingEvent.triggeringLocation!!.longitude,
                    "provider" to geofencingEvent.triggeringLocation!!.provider,
                    "time" to geofencingEvent.triggeringLocation!!.time,
                    "vertical_accuracy" to
                        if (Build.VERSION.SDK_INT >=
                            26
                        ) {
                            geofencingEvent.triggeringLocation!!.verticalAccuracyMeters.toInt()
                        } else {
                            0
                        },
                    "zone" to zone.substring(zone.indexOf("_") + 1),
                )
                ioScope.launch {
                    try {
                        val serverId = zone.split("_")[0].toIntOrNull() ?: return@launch
                        val enabled = isEnabled(latestContext, zoneLocation, serverId)
                        if (!enabled) return@launch
                        serverManager(
                            latestContext,
                        ).integrationRepository(serverId).fireEvent(zoneStatusEvent, zoneAttr as Map<String, Any>)
                        Timber.d("Event sent to Home Assistant")
                    } catch (e: Exception) {
                        Timber.e(e, "Unable to send event to Home Assistant")
                    }
                }
            }
        }

        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val sensorSettings = sensorDao.getSettings(zoneLocation.id)
        val minAccuracy = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURACY }?.value?.toIntOrNull()
            ?: DEFAULT_MINIMUM_ACCURACY
        sensorDao.add(
            SensorSetting(zoneLocation.id, SETTING_ACCURACY, minAccuracy.toString(), SensorSettingType.NUMBER),
        )

        val trigger = when (geofencingEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> LocationUpdateTrigger.GEOFENCE_ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> LocationUpdateTrigger.GEOFENCE_EXIT
            Geofence.GEOFENCE_TRANSITION_DWELL -> LocationUpdateTrigger.GEOFENCE_DWELL
            else -> null
        }
        if (geofencingEvent.triggeringLocation!!.accuracy > minAccuracy) {
            Timber.w("Geofence location accuracy didn't meet requirements, requesting new location.")
            logLocationUpdate(
                geofencingEvent.triggeringLocation,
                null,
                null,
                trigger,
                LocationHistoryItemResult.SKIPPED_ACCURACY,
            )
            requestSingleAccurateLocation()
        } else {
            getEnabledServers(latestContext, zoneLocation).forEach {
                ioScope.launch { sendLocationUpdate(geofencingEvent.triggeringLocation!!, it, trigger) }
            }
        }

        setupBackgroundLocation()
    }

    private suspend fun sendLocationUpdate(location: Location, serverId: Int, trigger: LocationUpdateTrigger?) {
        Timber.d(
            "Last Location: " +
                "\nCoords:(${location.latitude}, ${location.longitude})" +
                "\nAccuracy: ${location.accuracy}" +
                "\nBearing: ${location.bearing}",
        )
        var accuracy = 0
        if (location.accuracy.toInt() >= 0) {
            accuracy = location.accuracy.toInt()
        }
        val updateLocation: UpdateLocation
        val updateLocationString: String
        val updateLocationAs: String = getSendLocationAsSetting(serverId)
        if (updateLocationAs == SEND_LOCATION_AS_ZONE_ONLY) {
            val zones = getZones(serverId)
            val locationZone = zones
                .filter {
                    val passive = it.attributes["passive"] as? Boolean
                    val radius = it.attributes["radius"] as? Number
                    return@filter passive == false && radius != null && it.containsWithAccuracy(location)
                }
                .minByOrNull { (it.attributes["radius"] as? Number ?: Int.MAX_VALUE).toFloat() }

            val locationName = locationZone?.entityId?.split(".")?.getOrNull(1) ?: ZONE_NAME_NOT_HOME
            updateLocation = UpdateLocation(
                gps = null,
                gpsAccuracy = null,
                locationName = locationName,
                speed = null,
                altitude = null,
                course = null,
                verticalAccuracy = null,
            )
            updateLocationString = locationName
        } else {
            updateLocation = UpdateLocation(
                gps = listOf(location.latitude, location.longitude),
                gpsAccuracy = accuracy,
                locationName = null,
                speed = location.speed.toInt(),
                altitude = location.altitude.toInt(),
                course = location.bearing.toInt(),
                verticalAccuracy = if (Build.VERSION.SDK_INT >= 26) location.verticalAccuracyMeters.toInt() else 0,
            )
            updateLocationString = updateLocation.gps.toString()
        }

        val now = System.currentTimeMillis()

        Timber.d("Begin evaluating if location update should be skipped")
        if (now + 5000 < location.time && !highAccuracyModeEnabled) {
            Timber.d(
                "Skipping location update that came from the future. ${now + 5000} should always be greater than ${location.time}",
            )
            logLocationUpdate(location, updateLocation, serverId, trigger, LocationHistoryItemResult.SKIPPED_FUTURE)
            return
        }

        if (location.time < (lastLocationSend[serverId] ?: 0)) {
            Timber.d(
                "Skipping old location update since time is before the last one we sent, received: ${location.time} last sent: $lastLocationSend",
            )
            logLocationUpdate(location, updateLocation, serverId, trigger, LocationHistoryItemResult.SKIPPED_NOT_LATEST)
            return
        }

        if (now - location.time < 300000) {
            Timber.d(
                "Received location that is ${now - location.time} milliseconds old, ${location.time} compared to $now with source ${location.provider}",
            )
            if (lastUpdateLocation[serverId] == updateLocationString) {
                if (now < (lastLocationSend[serverId] ?: 0) + 900000) {
                    Timber.d("Duplicate location received, not sending to HA")
                    logLocationUpdate(
                        location,
                        updateLocation,
                        serverId,
                        trigger,
                        LocationHistoryItemResult.SKIPPED_DUPLICATE,
                    )
                    return
                }
            } else {
                if (now < (lastLocationSend[serverId] ?: 0) + 5000 &&
                    trigger?.isGeofence != true &&
                    !highAccuracyModeEnabled
                ) {
                    Timber.d(
                        "New location update not possible within 5 seconds, not sending to HA",
                    )
                    logLocationUpdate(
                        location,
                        updateLocation,
                        serverId,
                        trigger,
                        LocationHistoryItemResult.SKIPPED_DEBOUNCE,
                    )
                    return
                }
            }
        } else {
            Timber.d("Skipping location update due to old timestamp ${location.time} compared to $now")
            logLocationUpdate(location, updateLocation, serverId, trigger, LocationHistoryItemResult.SKIPPED_OLD)
            return
        }

        val geocodeIncludeLocation = getSetting(
            latestContext,
            GeocodeSensorManager.geocodedLocation,
            GeocodeSensorManager.SETTINGS_INCLUDE_LOCATION,
            SensorSettingType.TOGGLE,
            "false",
        ).toBoolean()

        ioScope.launch {
            try {
                serverManager(latestContext).integrationRepository(serverId).updateLocation(updateLocation)
                Timber.d("Location update sent successfully for $serverId as $updateLocationAs")
                lastLocationSend[serverId] = now
                lastUpdateLocation[serverId] = updateLocationString
                logLocationUpdate(location, updateLocation, serverId, trigger, LocationHistoryItemResult.SENT)

                // Update Geocoded Location Sensor
                if (geocodeIncludeLocation) {
                    val intent = Intent(latestContext, SensorReceiver::class.java)
                    intent.action = SensorReceiverBase.ACTION_UPDATE_SENSOR
                    intent.putExtra(
                        SensorReceiverBase.EXTRA_SENSOR_ID,
                        GeocodeSensorManager.geocodedLocation.id,
                    )
                    latestContext.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                Timber.e(e, "Could not update location for $serverId.")
                logLocationUpdate(location, updateLocation, serverId, trigger, LocationHistoryItemResult.FAILED_SEND)
            }
        }
    }

    private fun getLocationUpdateIntent(isGeofence: Boolean): PendingIntent {
        val intent = Intent(latestContext, LocationSensorManager::class.java)
        intent.action = if (isGeofence) ACTION_PROCESS_GEO else ACTION_PROCESS_LOCATION
        return PendingIntent.getBroadcast(
            latestContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun createLocationRequest(): LocationRequest {
        val locationRequest = LocationRequest.Builder(DEFAULT_LOCATION_INTERVAL).apply {
            // every 60 seconds
            setMaxUpdateDelayMillis(DEFAULT_LOCATION_MAX_WAIT_TIME) // every ~3.5 minutes
            setMinUpdateIntervalMillis(DEFAULT_LOCATION_FAST_INTERVAL) // every 30 seconds
            setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        }.build()

        return locationRequest
    }

    private suspend fun getZones(serverId: Int, forceRefresh: Boolean = false): List<Entity> {
        if (
            forceRefresh ||
            zones[serverId].isNullOrEmpty() ||
            (zonesLastReceived[serverId] ?: 0) < (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(4))
        ) {
            try {
                zones[serverId] = serverManager(latestContext).integrationRepository(serverId).getZones()
                zonesLastReceived[serverId] = System.currentTimeMillis()
            } catch (e: Exception) {
                Timber.e(e, "Error receiving zones from Home Assistant")
                if (forceRefresh) zones[serverId] = emptyList()
            }
        }
        return zones[serverId] ?: emptyList()
    }

    private suspend fun createGeofencingRequest(): GeofencingRequest? {
        val geofencingRequestBuilder = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)

        val highAccuracyTriggerRange = getHighAccuracyModeTriggerRange()
        val highAccuracyZones = getHighAccuracyModeZones(false)

        var geofenceCount = 0
        getEnabledServers(latestContext, zoneLocation).map { serverId ->
            ioScope.async {
                val configuredZones = getZones(serverId, forceRefresh = true)
                configuredZones.forEach {
                    addGeofenceToBuilder(geofencingRequestBuilder, serverId, it)
                    geofenceCount++
                    if (geofenceCount >= 100) {
                        return@async
                    }
                    if (highAccuracyTriggerRange > 0 && highAccuracyZones.contains("${serverId}_${it.entityId}")) {
                        addGeofenceToBuilder(geofencingRequestBuilder, serverId, it, highAccuracyTriggerRange)
                        geofenceCount++
                        if (geofenceCount >= 100) {
                            return@async
                        }
                    }
                }
                geofenceRegistered.add(serverId)
            }
        }.awaitAll()
        return if (geofenceCount > 0) geofencingRequestBuilder.build() else null
    }

    private fun addGeofenceToBuilder(
        geofencingRequestBuilder: GeofencingRequest.Builder,
        serverId: Int,
        zone: Entity,
        triggerRange: Int = 0,
    ) {
        val postRequestId = if (triggerRange > 0) "_expanded" else ""
        geofencingRequestBuilder
            .addGeofence(
                Geofence.Builder()
                    .setRequestId("${serverId}_" + zone.entityId + postRequestId)
                    .setCircularRegion(
                        (zone.attributes["latitude"] as Number).toDouble(),
                        (zone.attributes["longitude"] as Number).toDouble(),
                        (zone.attributes["radius"] as Number).toFloat() + triggerRange,
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build(),
            )
    }

    private suspend fun getHighAccuracyModeTriggerRange(): Int {
        val enabled = isEnabled(latestContext, zoneLocation)

        if (!enabled) return 0

        val highAccuracyTriggerRange = getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE,
            SensorSettingType.NUMBER,
            DEFAULT_TRIGGER_RANGE_METERS.toString(),
        )

        var highAccuracyTriggerRangeInt = highAccuracyTriggerRange.toIntOrNull() ?: DEFAULT_TRIGGER_RANGE_METERS
        if (highAccuracyTriggerRangeInt < 0) {
            highAccuracyTriggerRangeInt = DEFAULT_TRIGGER_RANGE_METERS

            val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
            sensorDao.add(
                SensorSetting(
                    backgroundLocation.id,
                    SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE,
                    highAccuracyTriggerRangeInt.toString(),
                    SensorSettingType.NUMBER,
                ),
            )
        }

        return highAccuracyTriggerRangeInt
    }

    private suspend fun getHighAccuracyModeZones(expandedZones: Boolean): List<String> {
        val enabled = isEnabled(latestContext, zoneLocation)

        if (!enabled) return emptyList()

        val highAccuracyZones = getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_ZONE,
            SensorSettingType.LIST_ZONES,
            "",
        )

        return if (highAccuracyZones.isNotEmpty()) {
            val expanded = if (expandedZones) "_expanded" else ""
            highAccuracyZones.split(",").map { it.trim() + expanded }
        } else {
            emptyList()
        }
    }

    private suspend fun requestSingleAccurateLocation() {
        if (!checkPermission(latestContext, singleAccurateLocation.id)) {
            Timber.w("Not getting single accurate location because of permissions.")
            return
        }
        if (!isEnabled(latestContext, singleAccurateLocation)) {
            Timber.w("Requested single accurate location but it is not enabled.")
            return
        }

        val now = System.currentTimeMillis()
        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val fullSensor = sensorDao.getFull(singleAccurateLocation.id).toSensorWithAttributes()
        val latestAccurateLocation =
            fullSensor?.attributes?.firstOrNull { it.name == "lastAccurateLocationRequest" }?.value?.toLongOrNull()
                ?: 0L

        val sensorSettings = sensorDao.getSettings(singleAccurateLocation.id)
        val minAccuracy = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURACY }?.value?.toIntOrNull()
            ?: DEFAULT_MINIMUM_ACCURACY
        sensorDao.add(
            SensorSetting(
                singleAccurateLocation.id,
                SETTING_ACCURACY,
                minAccuracy.toString(),
                SensorSettingType.NUMBER,
            ),
        )
        val minTimeBetweenUpdates = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURATE_UPDATE_TIME }?.value?.toIntOrNull()
            ?: 60000
        sensorDao.add(
            SensorSetting(
                singleAccurateLocation.id,
                SETTING_ACCURATE_UPDATE_TIME,
                minTimeBetweenUpdates.toString(),
                SensorSettingType.NUMBER,
            ),
        )

        // Only update accurate location at most once a minute
        if (now < latestAccurateLocation + minTimeBetweenUpdates) {
            Timber.d("Not requesting accurate location, last accurate location was too recent")
            return
        }
        sensorDao.add(Attribute(singleAccurateLocation.id, "lastAccurateLocationRequest", now.toString(), "string"))

        val maxRetries = 5
        val request = LocationRequest.Builder(10000).apply {
            setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            setMaxUpdates(maxRetries)
            setMinUpdateIntervalMillis(5000)
        }.build()
        try {
            LocationServices.getFusedLocationProviderClient(latestContext)
                .requestLocationUpdates(
                    request,
                    object : LocationCallback() {
                        val wakeLock: PowerManager.WakeLock? =
                            latestContext.getSystemService<PowerManager>()
                                ?.newWakeLock(
                                    PowerManager.PARTIAL_WAKE_LOCK,
                                    "HomeAssistant::AccurateLocation",
                                )?.apply {
                                    acquire(10 * 60 * 1000L) // 10 minutes
                                }
                        var numberCalls = 0
                        override fun onLocationResult(locationResult: LocationResult) {
                            numberCalls++
                            Timber.d(

                                "Got single accurate location update: ${locationResult.lastLocation}",
                            )
                            if (locationResult.equals(null)) {
                                Timber.w("No location provided.")
                                return
                            }

                            when {
                                locationResult.lastLocation!!.accuracy <= minAccuracy -> {
                                    Timber.d(
                                        "Location accurate enough, all done with high accuracy.",
                                    )
                                    ioScope.launch {
                                        locationResult.lastLocation?.let {
                                            getEnabledServers(
                                                latestContext,
                                                singleAccurateLocation,
                                            ).forEach { serverId ->
                                                sendLocationUpdate(
                                                    it,
                                                    serverId,
                                                    LocationUpdateTrigger.SINGLE_ACCURATE_LOCATION,
                                                )
                                            }
                                        }
                                    }
                                    if (wakeLock?.isHeld == true) wakeLock.release()
                                }

                                numberCalls >= maxRetries -> {
                                    Timber.d(
                                        "No location was accurate enough, sending our last location anyway",
                                    )
                                    if (locationResult.lastLocation!!.accuracy <= minAccuracy * 2) {
                                        ioScope.launch {
                                            getEnabledServers(
                                                latestContext,
                                                singleAccurateLocation,
                                            ).forEach { serverId ->
                                                sendLocationUpdate(
                                                    locationResult.lastLocation!!,
                                                    serverId,
                                                    LocationUpdateTrigger.SINGLE_ACCURATE_LOCATION,
                                                )
                                            }
                                        }
                                    }
                                    if (wakeLock?.isHeld == true) wakeLock.release()
                                }

                                else -> {
                                    Timber.w(
                                        "Location not accurate enough on retry $numberCalls of $maxRetries",
                                    )
                                }
                            }
                        }
                    },
                    Looper.getMainLooper(),
                )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get location data for single accurate sensor")
        }
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/location"
    }

    override val name: Int
        get() = commonR.string.sensor_name_location

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return when {
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    // TODO drop this requirement https://github.com/home-assistant/android/issues/5931
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            }
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    // TODO drop this requirement https://github.com/home-assistant/android/issues/5931
                    Manifest.permission.BLUETOOTH,
                )
            }
            else -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    // TODO drop this requirement https://github.com/home-assistant/android/issues/5931
                    Manifest.permission.BLUETOOTH,
                )
            }
        }
    }

    override suspend fun requestSensorUpdate(context: Context) {
        latestContext = context
        if (isEnabled(context, zoneLocation) || isEnabled(context, backgroundLocation)) {
            setupLocationTracking()
        }
        cleanupLocationHistory(context)
        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val sensorSetting = sensorDao.getSettings(singleAccurateLocation.id)
        val includeSensorUpdate =
            sensorSetting.firstOrNull { it.name == SETTING_INCLUDE_SENSOR_UPDATE }?.value ?: "false"
        if (includeSensorUpdate == "true") {
            if (isEnabled(context, singleAccurateLocation)) {
                context.sendBroadcast(
                    Intent(context, this.javaClass).apply {
                        action = ACTION_REQUEST_ACCURATE_LOCATION_UPDATE
                    },
                )
            }
        } else {
            sensorDao.add(
                SensorSetting(
                    singleAccurateLocation.id,
                    SETTING_INCLUDE_SENSOR_UPDATE,
                    "false",
                    SensorSettingType.TOGGLE,
                ),
            )
        }
    }

    private fun cleanupLocationHistory(context: Context) = ioScope.launch {
        handleInject(context)
        val historyDao = AppDatabase.getInstance(context).locationHistoryDao()
        val historyEnabled = prefsRepository.isLocationHistoryEnabled()
        if (historyEnabled) {
            historyDao.deleteBefore(System.currentTimeMillis() - HISTORY_DURATION)
        } else {
            historyDao.deleteAll()
        }
    }

    private fun logLocationUpdate(
        location: Location?,
        updateLocation: UpdateLocation?,
        serverId: Int?,
        trigger: LocationUpdateTrigger?,
        result: LocationHistoryItemResult,
    ) = ioScope.launch {
        if (location == null || !prefsRepository.isLocationHistoryEnabled()) return@launch

        val historyTrigger = when (trigger) {
            LocationUpdateTrigger.HIGH_ACCURACY_LOCATION -> LocationHistoryItemTrigger.FLP_FOREGROUND
            LocationUpdateTrigger.BACKGROUND_LOCATION -> LocationHistoryItemTrigger.FLP_BACKGROUND
            LocationUpdateTrigger.GEOFENCE_ENTER -> LocationHistoryItemTrigger.GEOFENCE_ENTER
            LocationUpdateTrigger.GEOFENCE_EXIT -> LocationHistoryItemTrigger.GEOFENCE_EXIT
            LocationUpdateTrigger.GEOFENCE_DWELL -> LocationHistoryItemTrigger.GEOFENCE_DWELL
            LocationUpdateTrigger.SINGLE_ACCURATE_LOCATION -> LocationHistoryItemTrigger.SINGLE_ACCURATE_LOCATION
            else -> LocationHistoryItemTrigger.UNKNOWN
        }

        try {
            // Use updateLocation to preserve the 'send location as' setting
            AppDatabase.getInstance(latestContext).locationHistoryDao().add(
                LocationHistoryItem(
                    trigger = historyTrigger,
                    result = result,
                    latitude = if (updateLocation != null) updateLocation.gps?.getOrNull(0) else location.latitude,
                    longitude = if (updateLocation != null) updateLocation.gps?.getOrNull(1) else location.longitude,
                    locationName = updateLocation?.locationName,
                    accuracy = updateLocation?.gpsAccuracy ?: location.accuracy.toInt(),
                    data = updateLocation?.toString(),
                    serverId = serverId,
                ),
            )
        } catch (e: Exception) {
            // Context is null? Shouldn't happen but don't let the app crash.
        }
    }

    private fun handleInject(context: Context) {
        // requestSensorUpdate is called outside onReceive, which usually handles injection.
        // Because we need the preferences for location history settings, inject it if required.
        if (!this::prefsRepository.isInitialized) {
            prefsRepository = EntryPointAccessors.fromApplication(
                context,
                LocationSensorManagerEntryPoint::class.java,
            ).prefsRepository()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocationSensorManagerEntryPoint {
        fun prefsRepository(): PrefsRepository
    }
}
