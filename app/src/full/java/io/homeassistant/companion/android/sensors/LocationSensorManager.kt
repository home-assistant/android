package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.GCJ2WGS
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.UpdateLocation
import io.homeassistant.companion.android.common.data.integration.ZoneAttributes
import io.homeassistant.companion.android.common.data.integration.containsWithAccuracy
import io.homeassistant.companion.android.common.notifications.DeviceCommandData
import io.homeassistant.companion.android.common.sensors.LocationSensorManagerBase
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.sensor.toSensorWithAttributes
import io.homeassistant.companion.android.location.HighAccuracyLocationService
import io.homeassistant.companion.android.notifications.MessagingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.Locale
import io.homeassistant.companion.android.common.R as commonR

var lastTime = 0L
var lastTime2 = 0L
var lastTime3 = 0L

@AndroidEntryPoint
class LocationSensorManager : LocationSensorManagerBase() {

    companion object {
        private const val SETTING_SEND_LOCATION_AS = "location_send_as"
        private const val SETTING_ACCURACY = "location_minimum_accuracy"
        private const val SETTING_ACCURATE_UPDATE_TIME = "location_minimum_time_updates"
        private const val SETTING_INCLUDE_SENSOR_UPDATE = "location_include_sensor_update"
        private const val SETTING_HIGH_ACCURACY_MODE = "location_ham_enabled"
        private const val SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL =
            "location_ham_update_interval"
        private const val SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES = "location_ham_only_bt_dev"
        private const val SETTING_HIGH_ACCURACY_MODE_ZONE = "location_ham_only_enter_zone"
        private const val SETTING_HIGH_ACCURACY_BT_ZONE_COMBINED = "location_ham_zone_bt_combined"
        private const val SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE =
            "location_ham_trigger_range"

        private const val SEND_LOCATION_AS_EXACT = "exact"
        private const val SEND_LOCATION_AS_ZONE_ONLY = "zone_only"
        private const val DEFAULT_MINIMUM_ACCURACY = 200
        private const val DEFAULT_UPDATE_INTERVAL_HA_SECONDS = 5
        private const val DEFAULT_TRIGGER_RANGE_METERS = 300

        private const val DEFAULT_LOCATION_INTERVAL: Long = 60000
        private const val DEFAULT_LOCATION_FAST_INTERVAL: Long = 30000
        private const val DEFAULT_LOCATION_MAX_WAIT_TIME: Long = 200000

        private const val ZONE_NAME_NOT_HOME = "not_home"

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
            updateType = SensorManager.BasicSensor.UpdateType.LOCATION
        )
        val zoneLocation = SensorManager.BasicSensor(
            "zone_background",
            "",
            commonR.string.basic_sensor_name_location_zone,
            commonR.string.sensor_description_location_zone,
            "mdi:map-marker-radius",
            updateType = SensorManager.BasicSensor.UpdateType.LOCATION
        )
        val singleAccurateLocation = SensorManager.BasicSensor(
            "accurate_location",
            "",
            commonR.string.basic_sensor_name_location_accurate,
            commonR.string.sensor_description_location_accurate,
            "mdi:crosshairs-gps",
            updateType = SensorManager.BasicSensor.UpdateType.LOCATION
        )

        val highAccuracyMode = SensorManager.BasicSensor(
            "high_accuracy_mode",
            "binary_sensor",
            commonR.string.basic_sensor_name_high_accuracy_mode,
            commonR.string.sensor_description_high_accuracy_mode,
            "mdi:crosshairs-gps",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )

        val highAccuracyUpdateInterval = SensorManager.BasicSensor(
            "high_accuracy_update_interval",
            "sensor",
            commonR.string.basic_sensor_name_high_accuracy_interval,
            commonR.string.sensor_description_high_accuracy_interval,
            "mdi:timer",
            unitOfMeasurement = "seconds",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        internal const val TAG = "LocBroadcastReceiver"

        private var isBackgroundLocationSetup = false
        private var isZoneLocationSetup = false

        private var lastLocationSend = 0L
        private var lastLocationReceived = 0L
        private var lastUpdateLocation = ""

        private var zones: Array<Entity<ZoneAttributes>> = emptyArray()
        private var zonesLastReceived = 0L

        private var geofenceRegistered = false

        private var lastHighAccuracyMode = false
        private var lastHighAccuracyUpdateInterval = DEFAULT_UPDATE_INTERVAL_HA_SECONDS
        private var forceHighAccuracyModeOn = false
        private var forceHighAccuracyModeOff = false
        private var highAccuracyModeEnabled = false

        private var lastEnteredGeoZones: MutableList<String> = ArrayList()
        private var lastExitedGeoZones: MutableList<String> = ArrayList()

        private var lastHighAccuracyTriggerRange: Int = 0
        private var lastHighAccuracyZones: List<String> = ArrayList()

        fun setHighAccuracyModeSetting(context: Context, enabled: Boolean) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            sensorDao.add(
                SensorSetting(
                    backgroundLocation.id,
                    SETTING_HIGH_ACCURACY_MODE,
                    enabled.toString(),
                    SensorSettingType.TOGGLE
                )
            )
        }

        fun getHighAccuracyModeIntervalSetting(context: Context): Int {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            val sensorSettings = sensorDao.getSettings(backgroundLocation.id)
            return sensorSettings.firstOrNull { it.name == SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL }?.value?.toIntOrNull()
                ?: DEFAULT_UPDATE_INTERVAL_HA_SECONDS
        }

        fun setHighAccuracyModeIntervalSetting(context: Context, updateInterval: Int) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            sensorDao.add(
                SensorSetting(
                    backgroundLocation.id,
                    SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL,
                    updateInterval.toString(),
                    SensorSettingType.NUMBER
                )
            )
        }
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    lateinit var latestContext: Context

    //声明AMapLocationClient类对象
    var mLocationClient: AMapLocationClient? = null

    var amapLocation: AMapLocation? = null


    //声明定位回调监听器
    private var mLocationListener = AMapLocationListener { location ->
        if (location.errorCode == 0) {

            amapLocation = location
            Log.d(TAG, "Amap Location -- ${location.latitude}")

            addressUpdata(latestContext)
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
                    SensorSettingType.NUMBER
                )
            )
            if (location.accuracy > minAccuracy) {
                Log.d(TAG, "Location accuracy didn't meet requirements, disregarding: $location")
            } else {
                val hm = GCJ2WGS.delta(location.latitude, location.longitude)
                location.latitude = hm["lat"]!!
                location.longitude = hm["lon"]!!
                runBlocking {
                    getEnabledServers(latestContext, singleAccurateLocation).forEach { serverId ->
                        sendLocationUpdate(location, serverId)
                    }
                }
            }
            //可在其中解析amapLocation获取相应内容。
        } else {
            //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
            Log.e(
                TAG, "Location Error, ErrCode:"
                        + location.errorCode + ", errInfo:"
                        + location.errorInfo
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        latestContext = context

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_REQUEST_LOCATION_UPDATES -> setupLocationTracking()

            ACTION_PROCESS_LOCATION,
            ACTION_PROCESS_HIGH_ACCURACY_LOCATION -> handleLocationUpdate(intent)

            ACTION_PROCESS_GEO -> handleLocationUpdate(intent)
            ACTION_REQUEST_ACCURATE_LOCATION_UPDATE -> requestSingleAccurateLocation()
            ACTION_FORCE_HIGH_ACCURACY -> {
                var command = intent.extras?.get("command")?.toString()
                when (command) {
                    DeviceCommandData.TURN_ON, DeviceCommandData.TURN_OFF, MessagingManager.FORCE_ON -> {
                        var turnOn = command != DeviceCommandData.TURN_OFF
                        if (turnOn) Log.d(TAG, "Forcing of high accuracy mode enabled")
                        else Log.d(TAG, "Forcing of high accuracy mode disabled")
                        forceHighAccuracyModeOn = turnOn
                        forceHighAccuracyModeOff = false
                        setHighAccuracyModeSetting(latestContext, turnOn)
                        ioScope.launch {
                            setupBackgroundLocation()
                        }
                    }

                    MessagingManager.FORCE_OFF -> {
                        Log.d(TAG, "High accuracy mode forced off")
                        forceHighAccuracyModeOn = false
                        forceHighAccuracyModeOff = true
                        ioScope.launch {
                            setupBackgroundLocation()
                        }
                    }

                    MessagingManager.HIGH_ACCURACY_SET_UPDATE_INTERVAL -> {
                        if (lastHighAccuracyMode)
                            restartHighAccuracyService(
                                getHighAccuracyModeIntervalSetting(
                                    latestContext
                                )
                            )
                    }
                }
            }

            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun setupLocationTracking() {
        if (!checkPermission(latestContext, backgroundLocation.id)) {
            Log.w(TAG, "Not starting location reporting because of permissions.")
            return
        }

        val backgroundEnabled = isEnabled(latestContext, backgroundLocation)
        val zoneEnabled = isEnabled(latestContext, zoneLocation)

        ioScope.launch {
            try {
                if (!backgroundEnabled && !zoneEnabled) {
                    removeAllLocationUpdateRequests()
                    isBackgroundLocationSetup = false
                    isZoneLocationSetup = false
                }
                if (!zoneEnabled && isZoneLocationSetup) {
                    isZoneLocationSetup = false
                }
                if (!backgroundEnabled && isBackgroundLocationSetup) {
                    removeBackgroundUpdateRequests()
                    stopHighAccuracyService()
                    isBackgroundLocationSetup = false
                }
                if (zoneEnabled && !isZoneLocationSetup) {
                    isZoneLocationSetup = true
                }

                val now = System.currentTimeMillis()
                if (
                    (!highAccuracyModeEnabled && isBackgroundLocationSetup) &&
                    ((lastLocationReceived + (DEFAULT_LOCATION_MAX_WAIT_TIME * 2L)) < now)
                ) {
                    Log.d(
                        TAG,
                        "Background location updates appear to have stopped, restarting location updates"
                    )
                    isBackgroundLocationSetup = false
                    removeBackgroundUpdateRequests()
                } else if (
                    highAccuracyModeEnabled &&
                    ((lastLocationReceived + (getHighAccuracyModeUpdateInterval().toLong() * 2000L)) < now)
                ) {
                    Log.d(
                        TAG,
                        "High accuracy mode appears to have stopped, restarting high accuracy mode"
                    )
                    isBackgroundLocationSetup = false
                    stopHighAccuracyService()
                }

                setupBackgroundLocation(backgroundEnabled, zoneEnabled)
            } catch (e: Exception) {
                Log.e(TAG, "Issue setting up location tracking", e)
            }
        }
    }

    private suspend fun setupBackgroundLocation(
        backgroundEnabled: Boolean? = null,
        zoneEnabled: Boolean? = null
    ) {
        var isBackgroundEnabled = backgroundEnabled
        var isZoneEnable = zoneEnabled
        if (isBackgroundEnabled == null) isBackgroundEnabled =
            isEnabled(latestContext, backgroundLocation)
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
                        Log.d(
                            TAG,
                            "High accuracy mode parameters changed. Enable high accuracy mode."
                        )
                        if (updateIntervalHighAccuracySeconds != lastHighAccuracyUpdateInterval) {
                            restartHighAccuracyService(updateIntervalHighAccuracySeconds)
                        } else {
                            removeBackgroundUpdateRequests()
                            startHighAccuracyService(updateIntervalHighAccuracySeconds)
                        }
                    } else {
                        Log.d(
                            TAG,
                            "High accuracy mode parameters changed. Disable high accuracy mode."
                        )
                        stopHighAccuracyService()
                        requestLocationUpdates()
                    }
                }

            }

            val highAccuracyModeSettingEnabled = getHighAccuracyModeSetting()
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL,
                highAccuracyModeSettingEnabled
            )
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES,
                highAccuracyModeSettingEnabled
            )
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_MODE_ZONE,
                highAccuracyModeSettingEnabled && isZoneEnable
            )
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE,
                highAccuracyModeSettingEnabled && isZoneEnable
            )
            enableDisableSetting(
                latestContext,
                backgroundLocation,
                SETTING_HIGH_ACCURACY_BT_ZONE_COMBINED,
                highAccuracyModeSettingEnabled && isZoneEnable
            )

            lastHighAccuracyZones = highAccuracyZones
            lastHighAccuracyTriggerRange = highAccuracyTriggerRange
            lastHighAccuracyMode = highAccuracyModeEnabled
            lastHighAccuracyUpdateInterval = updateIntervalHighAccuracySeconds

            serverManager.defaultServers.forEach {
                getSendLocationAsSetting(it.id) // Sets up the setting, value isn't used right now
            }// Sets up the setting, value isn't used right now
        }
    }

    private fun restartHighAccuracyService(intervalInSeconds: Int) {
        onSensorUpdated(
            latestContext,
            highAccuracyUpdateInterval,
            intervalInSeconds,
            highAccuracyUpdateInterval.statelessIcon,
            mapOf()
        )
        SensorReceiver.updateAllSensors(latestContext)
        HighAccuracyLocationService.restartService(latestContext, intervalInSeconds)
    }

    private fun startHighAccuracyService(intervalInSeconds: Int) {
        onSensorUpdated(
            latestContext,
            highAccuracyMode,
            true,
            highAccuracyMode.statelessIcon,
            mapOf()
        )
        onSensorUpdated(
            latestContext,
            highAccuracyUpdateInterval,
            intervalInSeconds,
            highAccuracyUpdateInterval.statelessIcon,
            mapOf()
        )
        SensorReceiver.updateAllSensors(latestContext)
        HighAccuracyLocationService.startService(latestContext, intervalInSeconds)
    }

    private fun stopHighAccuracyService() {
        onSensorUpdated(
            latestContext,
            highAccuracyMode,
            false,
            highAccuracyMode.statelessIcon,
            mapOf()
        )
        SensorReceiver.updateAllSensors(latestContext)
        HighAccuracyLocationService.stopService(latestContext)
    }

    private fun getHighAccuracyModeUpdateInterval(): Int {
        val updateIntervalHighAccuracySeconds = getSetting(
            latestContext,
            LocationSensorManager.backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL,
            SensorSettingType.NUMBER,
            DEFAULT_UPDATE_INTERVAL_HA_SECONDS.toString()
        )

        var updateIntervalHighAccuracySecondsInt =
            if (updateIntervalHighAccuracySeconds.isNullOrEmpty()) DEFAULT_UPDATE_INTERVAL_HA_SECONDS else updateIntervalHighAccuracySeconds.toInt()
        if (updateIntervalHighAccuracySecondsInt < 5) {
            updateIntervalHighAccuracySecondsInt = DEFAULT_UPDATE_INTERVAL_HA_SECONDS

            setHighAccuracyModeIntervalSetting(latestContext, updateIntervalHighAccuracySecondsInt)
        }
        return updateIntervalHighAccuracySecondsInt
    }

    private fun getHighAccuracyModeState(): Boolean {

        var highAccuracyMode = getHighAccuracyModeSetting()

        if (!highAccuracyMode) return false

        val shouldEnableHighAccuracyMode = shouldEnableHighAccuracyMode()

        // As soon as the high accuracy mode should be enabled, disable the force_on of high accuracy mode!
        if (shouldEnableHighAccuracyMode && forceHighAccuracyModeOn) {
            Log.d(
                TAG,
                "Forcing of high accuracy mode disabled, because high accuracy mode had to be enabled anyway."
            )
            forceHighAccuracyModeOn = false
        }

        // As soon as the high accuracy mode shouldn't be enabled, disable the force_off of high accuracy mode!
        if (!shouldEnableHighAccuracyMode && forceHighAccuracyModeOff) {
            Log.d(
                TAG,
                "Forcing off of high accuracy mode disabled, because high accuracy mode had to be disabled anyway."
            )
            forceHighAccuracyModeOff = false
        }

        return if (forceHighAccuracyModeOn) {
            Log.d(
                TAG,
                "High accuracy mode enabled, because command_high_accuracy_mode was used to turn it on"
            )
            true
        } else if (forceHighAccuracyModeOff) {
            Log.d(
                TAG,
                "High accuracy mode disabled, because command_high_accuracy_mode was used to force it off"
            )
            false
        } else {
            shouldEnableHighAccuracyMode
        }
    }

    private fun shouldEnableHighAccuracyMode(): Boolean {

        val highAccuracyModeBTDevicesSetting = getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES,
            SensorSettingType.LIST_BLUETOOTH,
            ""
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
            highAccuracyModeBTDevices.filter { !BluetoothAdapter.checkBluetoothAddress(it) }
                .forEach {
                    val foundDevices = bluetoothDevices.filter { btDevice -> btDevice.name == it }
                    if (foundDevices.isNotEmpty()) {
                        highAccuracyModeBTDevices.remove(it)
                        foundDevices.forEach { btDevice ->
                            if (!highAccuracyModeBTDevices.contains(btDevice.address))
                                highAccuracyModeBTDevices.add(btDevice.address)
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
                        SensorSettingType.LIST_BLUETOOTH
                    )
                )
            }

            btDevConnected =
                bluetoothDevices.any { it.connected && highAccuracyModeBTDevices.contains(it.address) }

            if (!forceHighAccuracyModeOn && !forceHighAccuracyModeOff) {
                if (!btDevConnected) Log.d(
                    TAG,
                    "High accuracy mode disabled, because defined ($highAccuracyModeBTDevices) bluetooth device(s) not connected (Connected devices: $bluetoothDevices)"
                )
                else Log.d(
                    TAG,
                    "High accuracy mode enabled, because defined ($highAccuracyModeBTDevices) bluetooth device(s) connected (Connected devices: $bluetoothDevices)"
                )
            }
        }

        if (highAccuracyZones.isNotEmpty()) {
            constraintsUsed = true

            // (Expanded) Zone entered
            val zoneExpEntered =
                lastEnteredGeoZones.isNotEmpty() && highAccuracyExpZones.containsAll(
                    lastEnteredGeoZones
                )

            // Exits events are only used if expended zones are used. The exit events are used to determine the enter of the expanded zone from the original zone
            // Zone exited
            val zoneExited =
                useTriggerRange && lastExitedGeoZones.isNotEmpty() && highAccuracyZones.containsAll(
                    lastExitedGeoZones
                )

            inZone = zoneExpEntered || zoneExited

            if (!forceHighAccuracyModeOn && !forceHighAccuracyModeOff) {
                if (!inZone) Log.d(
                    TAG,
                    "High accuracy mode disabled, because not in zone $highAccuracyExpZones"
                )
                else Log.d(TAG, "High accuracy mode enabled, because in zone $highAccuracyExpZones")
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

    private fun getHighAccuracyModeSetting(): Boolean {
        return getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE,
            SensorSettingType.TOGGLE,
            "false"
        ).toBoolean()
    }

    private fun getHighAccuracyBTZoneCombinedSetting(): Boolean {
        return getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_BT_ZONE_COMBINED,
            SensorSettingType.TOGGLE,
            "false"
        ).toBoolean()
    }

    private fun getSendLocationAsSetting(serverId: Int): String {
        return if (serverManager.getServer(serverId)?.version?.isAtLeast(2022, 2, 0) == true) {
            getSetting(
                context = latestContext,
                sensor = backgroundLocation,
                settingName = SETTING_SEND_LOCATION_AS,
                settingType = SensorSettingType.LIST,
                entries = listOf(
                    SEND_LOCATION_AS_EXACT,
                    SEND_LOCATION_AS_ZONE_ONLY
                ),
                default = SEND_LOCATION_AS_EXACT
            )
        } else {
            SEND_LOCATION_AS_EXACT
        }
    }

    private fun removeAllLocationUpdateRequests() {
        Log.d(TAG, "Removing all location requests.")
        removeBackgroundUpdateRequests()
    }

    private fun removeBackgroundUpdateRequests() {
        mLocationClient?.stopLocation()
    }

    private fun requestLocationUpdates() {
        if (!checkPermission(latestContext, backgroundLocation.id)) {
            Log.w(TAG, "Not registering for location updates because of permissions.")
            return
        }
        Log.d(TAG, "Registering for location updates.")
        var amapKey = latestContext.getSharedPreferences("config", Context.MODE_PRIVATE)
            .getString("amapKey", null)
        if (amapKey.isNullOrEmpty()) {
            getLocation(latestContext)
        } else {
            AMapLocationClient.updatePrivacyShow(latestContext, true, true)
            AMapLocationClient.updatePrivacyAgree(latestContext, true)
            AMapLocationClient.setApiKey(amapKey)

            mLocationClient = AMapLocationClient(latestContext)

            mLocationClient!!.setLocationListener(mLocationListener)
            val mLocationOption = AMapLocationClientOption()

            if (lastHighAccuracyMode) {
                mLocationOption.locationMode =
                    AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            } else {
                mLocationOption.locationMode =
                    AMapLocationClientOption.AMapLocationMode.Battery_Saving
            }
            if (lastHighAccuracyUpdateInterval > 999999) {
                mLocationOption.isOnceLocation = true
                mLocationOption.isOnceLocationLatest = true
            } else {
                if (lastHighAccuracyUpdateInterval < 10) lastHighAccuracyUpdateInterval = 10
                mLocationOption.interval = lastHighAccuracyUpdateInterval * 1000L
                mLocationOption.isOnceLocation = false

            }
            mLocationClient!!.setLocationOption(mLocationOption)
            mLocationClient!!.startLocation()
        }

    }

    private fun getLocation(context: Context) {
        val locationManager =
            context.getSystemService(LOCATION_SERVICE) as LocationManager

        if (lastTime != 0L && System.currentTimeMillis() - lastTime < 60000) return
        lastTime = System.currentTimeMillis()

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            180000,
            0f,
            object : LocationListener {
                override fun onLocationChanged(it: Location) {
                    runBlocking {
                        getEnabledServers(
                            latestContext,
                            singleAccurateLocation
                        ).forEach { serverId ->
                            sendLocationUpdate(it, serverId)
                        }
                    }

                    if (lastTime2 != 0L && System.currentTimeMillis() - lastTime2 < 60000) return
                    lastTime2 = System.currentTimeMillis()
                    Log.e("onLocationChanged", "${it.latitude}:${it.longitude}")
                    getGeocodedLocation(it)
                }

            }, Looper.getMainLooper()
        )

        if (lastTime2 != 0L && System.currentTimeMillis() - lastTime2 > 120000) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                180000,
                0f,
                object : LocationListener {
                    override fun onLocationChanged(it: Location) {
                        runBlocking {
                            getEnabledServers(
                                latestContext,
                                singleAccurateLocation
                            ).forEach { serverId ->
                                sendLocationUpdate(it, serverId)
                            }
                        }
                        if (lastTime3 != 0L && System.currentTimeMillis() - lastTime3 < 180000) return
                        lastTime3 = System.currentTimeMillis()
                        Log.e("onLocationChanged2", "${it.latitude}:${it.longitude}")
                        getGeocodedLocation(it)
                    }

                }, Looper.getMainLooper()
            )
        }

        runBlocking {
            getEnabledServers(
                latestContext,
                singleAccurateLocation
            ).forEach { serverId ->
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?.let {
                        Log.e("getLastKnownLocation", "${it.latitude}:${it.longitude}")
                        sendLocationUpdate(it, serverId)
                    }
            }
        }
        // gps
        // return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        // 网络定位
        //return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }

    private fun getGeocodedLocation(it: Location) {
        try {
            val latitude: Double = it.latitude
            val longitude: Double = it.longitude
            // 地理编辑器  如果想获取地理位置 使用地理编辑器将经纬度转换为省市区
            val geocoder = Geocoder(latestContext, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) {
                    val address: Address = it[0]
                    sendGeocodedLocation(address)
                }
            } else {
                runBlocking {
                    val it = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!it.isNullOrEmpty()) {
                        val address: Address = it[0]
                        sendGeocodedLocation(address)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun sendGeocodedLocation(address: Address) {
        val mAddressLine: String = address.getAddressLine(0)
        onSensorUpdated(
            latestContext,
            GeocodeSensorManager.geocodedLocation,
            mAddressLine,
            "mdi:map",
            mapOf(
                "Latitude" to address.latitude,
                "Longitude" to address.longitude,
            )
        )
    }

    private fun handleLocationUpdate(intent: Intent) {
        Log.d(TAG, "Received location update.")
        lastLocationReceived = System.currentTimeMillis()
        if (mLocationClient != null) {
            mLocationClient?.startLocation()
        } else {
            requestLocationUpdates()
        }
    }

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (DisabledLocationHandler.hasGPS(context)) {
            listOf(
                singleAccurateLocation,
                backgroundLocation,
                zoneLocation,
                highAccuracyMode,
                highAccuracyUpdateInterval
            )
        } else {
            listOf(backgroundLocation, zoneLocation, highAccuracyMode, highAccuracyUpdateInterval)
        }
    }

    private fun sendLocationUpdate(
        location: Location,
        serverId: Int,
        geofenceUpdate: Boolean = false,
    ) {
        Log.d(
            TAG,
            "Last Location: " +
                    "\nCoords:(${location.latitude}, ${location.longitude})" +
                    "\nAccuracy: ${location.accuracy}" +
                    "\nBearing: ${location.bearing}"
        )
        var accuracy = 0
        if (location.accuracy.toInt() >= 0) {
            accuracy = location.accuracy.toInt()
            if (accuracy > 15) return
        }
        val updateLocation: UpdateLocation
        val updateLocationAs: String
        val updateLocationString: String
        runBlocking {
            updateLocationAs = getSendLocationAsSetting(serverId)
            if (updateLocationAs == SEND_LOCATION_AS_ZONE_ONLY) {
                // val zones = getZones(serverId)
                val locationZone = zones
                    .filter { !it.attributes.passive && it.containsWithAccuracy(location) }
                    .minByOrNull { it.attributes.radius }
                updateLocation = UpdateLocation(
                    gps = null,
                    gpsAccuracy = null,
                    locationName = locationZone?.entityId?.split(".")?.get(1) ?: ZONE_NAME_NOT_HOME,
                    speed = null,
                    altitude = null,
                    course = null,
                    verticalAccuracy = null
                )
                updateLocationString = updateLocation.locationName!!
            } else {
                updateLocation = UpdateLocation(
                    gps = arrayOf(location.latitude, location.longitude),
                    gpsAccuracy = accuracy,
                    locationName = null,
                    speed = location.speed.toInt(),
                    altitude = location.altitude.toInt(),
                    course = location.bearing.toInt(),
                    verticalAccuracy = if (Build.VERSION.SDK_INT >= 26) location.verticalAccuracyMeters.toInt() else 0
                )
                updateLocationString = updateLocation.gps.contentToString()
            }
        }

        val now = System.currentTimeMillis()

//        Log.d(TAG, "Begin evaluating if location update should be skipped")
//        if (now + 5000 < location.time && !highAccuracyModeEnabled) {
//            Log.d(
//                TAG,
//                "Skipping location update that came from the future. ${now + 5000} should always be greater than ${location.time}"
//            )
//            return
//        }

        if (location.time < lastLocationSend) {
            Log.d(
                TAG,
                "Skipping old location update since time is before the last one we sent, received: ${location.time} last sent: $lastLocationSend"
            )
            return
        }

        if (now - location.time < 300000) {
            Log.d(
                TAG,
                "Received location that is ${now - location.time} milliseconds old, ${location.time} compared to $now with source ${location.provider}"
            )
            if (lastUpdateLocation == updateLocationString) {
                if (now < lastLocationSend + 900000) {
                    Log.d(TAG, "Duplicate location received, not sending to HA")
                    return
                }
            } else {
                if (now < lastLocationSend + 5000 && !geofenceUpdate && !highAccuracyModeEnabled) {
                    Log.d(
                        TAG,
                        "New location update not possible within 5 seconds, not sending to HA"
                    )
                    return
                }
            }
        } else {
            Log.d(
                TAG,
                "Skipping location update due to old timestamp ${location.time} compared to $now"
            )
            return
        }
        lastLocationSend = now
        lastUpdateLocation = updateLocationString

        val geocodeIncludeLocation = getSetting(
            latestContext,
            GeocodeSensorManager.geocodedLocation,
            GeocodeSensorManager.SETTINGS_INCLUDE_LOCATION,
            SensorSettingType.TOGGLE,
            "false"
        ).toBoolean()

        ioScope.launch {
            try {
                serverManager.integrationRepository(serverId).updateLocation(updateLocation)
                Log.d(TAG, "Location update sent successfully for $serverId as $updateLocationAs")

                // Update Geocoded Location Sensor
                if (geocodeIncludeLocation) {
                    val intent = Intent(latestContext, SensorReceiver::class.java)
                    intent.action = SensorReceiverBase.ACTION_UPDATE_SENSOR
                    intent.putExtra(
                        SensorReceiverBase.EXTRA_SENSOR_ID,
                        GeocodeSensorManager.geocodedLocation.id
                    )
                    latestContext.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not update location.", e)
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }


    private fun getHighAccuracyModeTriggerRange(): Int {
        val enabled = isEnabled(latestContext, zoneLocation)

        if (!enabled) return 0

        val highAccuracyTriggerRange = getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE,
            SensorSettingType.NUMBER,
            DEFAULT_TRIGGER_RANGE_METERS.toString()
        )

        var highAccuracyTriggerRangeInt =
            highAccuracyTriggerRange.toIntOrNull() ?: DEFAULT_TRIGGER_RANGE_METERS
        if (highAccuracyTriggerRangeInt < 0) {
            highAccuracyTriggerRangeInt = DEFAULT_TRIGGER_RANGE_METERS

            val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
            sensorDao.add(
                SensorSetting(
                    backgroundLocation.id,
                    SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE,
                    highAccuracyTriggerRangeInt.toString(),
                    SensorSettingType.NUMBER
                )
            )
        }

        return highAccuracyTriggerRangeInt
    }

    private fun getHighAccuracyModeZones(expandedZones: Boolean): List<String> {
        val enabled = isEnabled(latestContext, zoneLocation)

        if (!enabled) return emptyList()

        val highAccuracyZones = getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_ZONE,
            SensorSettingType.LIST_ZONES,
            ""
        )

        return if (highAccuracyZones.isNotEmpty()) {
            val expanded = if (expandedZones) "_expanded" else ""
            highAccuracyZones.split(",").map { it.trim() + expanded }
        } else {
            emptyList()
        }
    }

    private fun requestSingleAccurateLocation() {
        if (!checkPermission(latestContext, singleAccurateLocation.id)) {
            Log.w(TAG, "Not getting single accurate location because of permissions.")
            return
        }
        if (!isEnabled(latestContext, singleAccurateLocation)) {
            Log.w(TAG, "Requested single accurate location but it is not enabled.")
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
                SensorSettingType.NUMBER
            )
        )
        val minTimeBetweenUpdates = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURATE_UPDATE_TIME }?.value?.toIntOrNull()
            ?: 60000
        sensorDao.add(
            SensorSetting(
                singleAccurateLocation.id,
                SETTING_ACCURATE_UPDATE_TIME,
                minTimeBetweenUpdates.toString(),
                SensorSettingType.NUMBER
            )
        )

        // Only update accurate location at most once a minute
        if (now < latestAccurateLocation + minTimeBetweenUpdates) {
            Log.d(TAG, "Not requesting accurate location, last accurate location was too recent")
            return
        }
        sensorDao.add(
            Attribute(
                singleAccurateLocation.id,
                "lastAccurateLocationRequest",
                now.toString(),
                "string"
            )
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/location"
    }

    override val name: Int
        get() = commonR.string.sensor_name_location

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            }

            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH
                )
            }

            else -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH
                )
            }
        }
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        latestContext = context
        if (isEnabled(context, zoneLocation) || isEnabled(context, backgroundLocation))
            setupLocationTracking()
        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val sensorSetting = sensorDao.getSettings(singleAccurateLocation.id)
        val includeSensorUpdate =
            sensorSetting.firstOrNull { it.name == SETTING_INCLUDE_SENSOR_UPDATE }?.value ?: "false"
        if (includeSensorUpdate == "true") {
            if (isEnabled(context, singleAccurateLocation)) {
                context.sendBroadcast(
                    Intent(context, this.javaClass).apply {
                        action = ACTION_REQUEST_ACCURATE_LOCATION_UPDATE
                    }
                )
            }
        } else
            sensorDao.add(
                SensorSetting(
                    singleAccurateLocation.id,
                    SETTING_INCLUDE_SENSOR_UPDATE,
                    "false",
                    SensorSettingType.TOGGLE
                )
            )
    }

    private fun addressUpdata(context: Context) {
        var addressStr = amapLocation!!.address
        val attributes = amapLocation.let {
            mapOf(
                "Administrative Area" to it!!.district,
                "Country" to it.city,
                "accuracy" to it.accuracy,
                "altitude" to it.altitude,
                "bearing" to it.bearing,
                "provider" to it.provider,
                "time" to it.time,
                "Locality" to it.province,
                "Latitude" to it.latitude,
                "Longitude" to it.longitude,
                "Postal Code" to it.cityCode,
                "Thoroughfare" to it.street,
                //"ISO Country Code" to it.cityCode,
                "vertical_accuracy" to if (Build.VERSION.SDK_INT >= 26) it.verticalAccuracyMeters.toInt() else 0,
            )
        }
        if (TextUtils.isEmpty(addressStr)) {
            addressStr =
                amapLocation!!.city + amapLocation!!.district + amapLocation!!.street + amapLocation!!.aoiName + amapLocation!!.floor
        }
        if (TextUtils.isEmpty(addressStr)) {
            Log.d(TAG, "addressStr--" + amapLocation!!.locationDetail)
            return
        }
        onSensorUpdated(
            context,
            GeocodeSensorManager.geocodedLocation,
            addressStr,
            "mdi:map",
            attributes
        )
    }

}
