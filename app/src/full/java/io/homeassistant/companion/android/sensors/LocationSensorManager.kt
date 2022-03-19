package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
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
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.UpdateLocation
import io.homeassistant.companion.android.common.data.integration.ZoneAttributes
import io.homeassistant.companion.android.common.sensors.LocationSensorManagerBase
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.location.HighAccuracyLocationService
import io.homeassistant.companion.android.notifications.MessagingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class LocationSensorManager : LocationSensorManagerBase() {

    companion object {
        private const val SETTING_ACCURACY = "location_minimum_accuracy"
        private const val SETTING_ACCURATE_UPDATE_TIME = "location_minimum_time_updates"
        private const val SETTING_INCLUDE_SENSOR_UPDATE = "location_include_sensor_update"
        private const val SETTING_HIGH_ACCURACY_MODE = "location_ham_enabled"
        private const val SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL = "location_ham_update_interval"
        private const val SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES = "location_ham_only_bt_dev"
        private const val SETTING_HIGH_ACCURACY_MODE_ZONE = "location_ham_only_enter_zone"
        private const val SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE = "location_ham_trigger_range"

        private const val DEFAULT_MINIMUM_ACCURACY = 200
        private const val DEFAULT_UPDATE_INTERVAL_HA_SECONDS = 5
        private const val DEFAULT_TRIGGER_RANGE_METERS = 300

        const val ACTION_REQUEST_LOCATION_UPDATES =
            "io.homeassistant.companion.android.background.REQUEST_UPDATES"
        const val ACTION_REQUEST_ACCURATE_LOCATION_UPDATE =
            "io.homeassistant.companion.android.background.REQUEST_ACCURATE_UPDATE"
        const val ACTION_PROCESS_LOCATION =
            "io.homeassistant.companion.android.background.PROCESS_UPDATES"
        const val ACTION_PROCESS_GEO =
            "io.homeassistant.companion.android.background.PROCESS_GEOFENCE"
        const val ACTION_FORCE_HIGH_ACCURACY =
            "io.homeassistant.companion.android.background.FORCE_HIGH_ACCURACY"

        val backgroundLocation = SensorManager.BasicSensor(
            "location_background",
            "",
            commonR.string.basic_sensor_name_location_background,
            commonR.string.sensor_description_location_background
        )
        val zoneLocation = SensorManager.BasicSensor(
            "zone_background",
            "",
            commonR.string.basic_sensor_name_location_zone,
            commonR.string.sensor_description_location_zone
        )
        val singleAccurateLocation = SensorManager.BasicSensor(
            "accurate_location",
            "",
            commonR.string.basic_sensor_name_location_accurate,
            commonR.string.sensor_description_location_accurate
        )

        val highAccuracyMode = SensorManager.BasicSensor(
            "high_accuracy_mode",
            "binary_sensor",
            commonR.string.basic_sensor_name_high_accuracy_mode,
            commonR.string.sensor_description_high_accuracy_mode,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        internal const val TAG = "LocBroadcastReceiver"

        private var geofencingClient: GeofencingClient? = null
        private var fusedLocationProviderClient: FusedLocationProviderClient? = null

        private var isBackgroundLocationSetup = false
        private var isZoneLocationSetup = false

        private var lastLocationSend = 0L
        private var lastUpdateLocation = ""

        private var geofenceRegistered = false

        private var lastHighAccuracyMode = false
        private var lastHighAccuracyUpdateInterval = DEFAULT_MINIMUM_ACCURACY
        private var forceHighAccuracyModeOn = false
        private var highAccuracyModeEnabled = false

        private var lastEnteredGeoZones: MutableList<String> = ArrayList()
        private var lastExitedGeoZones: MutableList<String> = ArrayList()

        private var lastHighAccuracyTriggerRange: Int = 0
        private var lastHighAccuracyZones: List<String> = ArrayList()

        fun setHighAccuracyModeSetting(context: Context, enabled: Boolean) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            sensorDao.add(SensorSetting(backgroundLocation.id, SETTING_HIGH_ACCURACY_MODE, enabled.toString(), "toggle"))
        }

        fun getHighAccuracyModeIntervalSetting(context: Context): Int {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            val sensorSettings = sensorDao.getSettings(backgroundLocation.id)
            return sensorSettings.firstOrNull { it.name == SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL }?.value?.toIntOrNull() ?: DEFAULT_UPDATE_INTERVAL_HA_SECONDS
        }

        fun setHighAccuracyModeIntervalSetting(context: Context, updateInterval: Int) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            sensorDao.add(SensorSetting(backgroundLocation.id, SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL, updateInterval.toString(), "number"))
        }
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    lateinit var latestContext: Context

    override fun onReceive(context: Context, intent: Intent) {
        latestContext = context

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_REQUEST_LOCATION_UPDATES -> setupLocationTracking()
            ACTION_PROCESS_LOCATION -> handleLocationUpdate(intent)
            ACTION_PROCESS_GEO -> handleGeoUpdate(intent)
            ACTION_REQUEST_ACCURATE_LOCATION_UPDATE -> requestSingleAccurateLocation()
            ACTION_FORCE_HIGH_ACCURACY -> {
                var command = intent.extras?.get("command")?.toString()
                when (command) {
                    MessagingManager.TURN_ON, MessagingManager.TURN_OFF -> {
                        var turnOn = command == MessagingManager.TURN_ON
                        if (turnOn) Log.d(TAG, "Forcing of high accuracy mode enabled")
                        else Log.d(TAG, "Forcing of high accuracy mode disabled")
                        forceHighAccuracyModeOn = turnOn
                        setHighAccuracyModeSetting(latestContext, turnOn)
                        ioScope.launch {
                            setupBackgroundLocation()
                        }
                    }

                    MessagingManager.HIGH_ACCURACY_SET_UPDATE_INTERVAL -> {
                        if (lastHighAccuracyMode)
                            restartHighAccuracyService(getHighAccuracyModeIntervalSetting(latestContext))
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

        val backgroundEnabled = isEnabled(latestContext, backgroundLocation.id)
        val zoneEnabled = isEnabled(latestContext, zoneLocation.id)

        ioScope.launch {
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

                setupBackgroundLocation(backgroundEnabled, zoneEnabled)
            } catch (e: Exception) {
                Log.e(TAG, "Issue setting up location tracking", e)
            }
        }
    }

    private suspend fun setupBackgroundLocation(backgroundEnabled: Boolean? = null, zoneEnabled: Boolean? = null) {
        var isBackgroundEnabled = backgroundEnabled
        var isZoneEnable = zoneEnabled
        if (isBackgroundEnabled == null) isBackgroundEnabled = isEnabled(latestContext, backgroundLocation.id)
        if (isZoneEnable == null) isZoneEnable = isEnabled(latestContext, zoneLocation.id)

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
                        Log.d(TAG, "High accuracy mode parameters changed. Enable high accuracy mode.")
                        if (updateIntervalHighAccuracySeconds != lastHighAccuracyUpdateInterval) {
                            restartHighAccuracyService(updateIntervalHighAccuracySeconds)
                        } else {
                            removeBackgroundUpdateRequests()
                            startHighAccuracyService(updateIntervalHighAccuracySeconds)
                        }
                    } else {
                        Log.d(TAG, "High accuracy mode parameters changed. Disable high accuracy mode.")
                        stopHighAccuracyService()
                        requestLocationUpdates()
                    }
                }

                if (highAccuracyTriggerRange != lastHighAccuracyTriggerRange ||
                    highAccuracyZones != lastHighAccuracyZones
                ) {
                    Log.d(TAG, "High accuracy mode geo parameters changed. Reconfigure zones.")
                    removeGeofenceUpdateRequests()
                    requestZoneUpdates()
                }
            }

            val highAccuracyModeSettingEnabled = getHighAccuracyModeSetting()
            enableDisableSetting(latestContext, backgroundLocation, SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL, highAccuracyModeSettingEnabled)
            enableDisableSetting(latestContext, backgroundLocation, SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES, highAccuracyModeSettingEnabled)
            enableDisableSetting(latestContext, backgroundLocation, SETTING_HIGH_ACCURACY_MODE_ZONE, highAccuracyModeSettingEnabled && isZoneEnable)
            enableDisableSetting(latestContext, backgroundLocation, SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE, highAccuracyModeSettingEnabled && isZoneEnable)

            lastHighAccuracyZones = highAccuracyZones
            lastHighAccuracyTriggerRange = highAccuracyTriggerRange
            lastHighAccuracyMode = highAccuracyModeEnabled
            lastHighAccuracyUpdateInterval = updateIntervalHighAccuracySeconds
        }
    }

    private fun restartHighAccuracyService(intervalInSeconds: Int) {
        HighAccuracyLocationService.restartService(latestContext, intervalInSeconds)
    }

    private fun startHighAccuracyService(intervalInSeconds: Int) {
        onSensorUpdated(
            latestContext,
            highAccuracyMode,
            true,
            "mdi:crosshairs-gps",
            mapOf()
        )
        SensorWorker.start(latestContext)
        HighAccuracyLocationService.startService(latestContext, intervalInSeconds)
    }

    private fun stopHighAccuracyService() {
        onSensorUpdated(
            latestContext,
            highAccuracyMode,
            false,
            "mdi:crosshairs-gps",
            mapOf()
        )
        SensorWorker.start(latestContext)
        HighAccuracyLocationService.stopService(latestContext)
    }

    private fun getHighAccuracyModeUpdateInterval(): Int {
        val updateIntervalHighAccuracySeconds = getSetting(
            latestContext,
            LocationSensorManager.backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL,
            "number",
            DEFAULT_UPDATE_INTERVAL_HA_SECONDS.toString()
        )

        var updateIntervalHighAccuracySecondsInt = if (updateIntervalHighAccuracySeconds.isNullOrEmpty()) DEFAULT_UPDATE_INTERVAL_HA_SECONDS else updateIntervalHighAccuracySeconds.toInt()
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

        // As soon as the high accuracy mode should be enabled, disable the force of high accuracy mode!
        if (shouldEnableHighAccuracyMode) {
            Log.d(TAG, "Forcing of high accuracy mode disabled, because high accuracy mode had to be enabled anyway.")
            forceHighAccuracyModeOn = false
        }

        return if (!forceHighAccuracyModeOn) {
            shouldEnableHighAccuracyMode
        } else {
            Log.d(TAG, "High accuracy mode enabled, because command_high_accuracy_mode was used to turn it on")
            true
        }
    }

    private fun shouldEnableHighAccuracyMode(): Boolean {

        val highAccuracyModeBTDevices = getSetting(
            latestContext,
            LocationSensorManager.backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_BLUETOOTH_DEVICES,
            "list-bluetooth",
            ""
        )

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

        if (!highAccuracyModeBTDevices.isNullOrEmpty()) {
            constraintsUsed = true

            val bluetoothDevices = BluetoothUtils.getBluetoothDevices(latestContext)
            btDevConnected = bluetoothDevices.any { it.connected && highAccuracyModeBTDevices.contains(it.name) }

            if (!forceHighAccuracyModeOn) {
                if (!btDevConnected) Log.d(TAG, "High accuracy mode disabled, because defined ($highAccuracyModeBTDevices) bluetooth device(s) not connected (Connected devices: $bluetoothDevices)")
                else Log.d(TAG, "High accuracy mode enabled, because defined ($highAccuracyModeBTDevices) bluetooth device(s) connected (Connected devices: $bluetoothDevices)")
            }
        }

        if (highAccuracyZones.isNotEmpty()) {
            constraintsUsed = true

            // (Expanded) Zone entered
            val zoneExpEntered = lastEnteredGeoZones.isNotEmpty() && highAccuracyExpZones.containsAll(lastEnteredGeoZones)

            // Exits events are only used if expended zones are used. The exit events are used to determine the enter of the expanded zone from the original zone
            // Zone exited
            val zoneExited = useTriggerRange && lastExitedGeoZones.isNotEmpty() && highAccuracyZones.containsAll(lastExitedGeoZones)

            inZone = zoneExpEntered || zoneExited

            if (!forceHighAccuracyModeOn) {
                if (!inZone) Log.d(TAG, "High accuracy mode disabled, because not in zone $highAccuracyExpZones")
                else Log.d(TAG, "High accuracy mode enabled, because in zone $highAccuracyExpZones")
            }
        }

        // true = High accuracy mode enabled
        // false = High accuracy mode disabled
        //
        // if either BT Device is connected or in Zone -> High accuracy mode enabled (true)
        // Else (NO BT dev connected and NOT in Zone), if min. one constraint is used ->  High accuracy mode disabled (false)
        //                                             if no constraint is used ->  High accuracy mode enabled (true)
        return if (btDevConnected || inZone) {
            true
        } else {
            !constraintsUsed
        }
    }

    private fun getHighAccuracyModeSetting(): Boolean {
        return getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE,
            "toggle",
            "false"
        ).toBoolean()
    }

    private fun removeAllLocationUpdateRequests() {
        Log.d(TAG, "Removing all location requests.")
        removeBackgroundUpdateRequests()
        removeGeofenceUpdateRequests()
    }

    private fun removeBackgroundUpdateRequests() {
        if (fusedLocationProviderClient != null) {
            Log.d(TAG, "Removing background location requests.")
            val backgroundIntent = getLocationUpdateIntent(false)
            fusedLocationProviderClient?.removeLocationUpdates(backgroundIntent)
        } else Log.d(TAG, "Cannot remove background location requests. Location provider is not set.")
    }

    private fun removeGeofenceUpdateRequests() {
        if (geofencingClient != null) {
            Log.d(TAG, "Removing geofence location requests.")
            val zoneIntent = getLocationUpdateIntent(true)
            geofencingClient?.removeGeofences(zoneIntent)
            geofenceRegistered = false
            lastEnteredGeoZones.clear()
            lastExitedGeoZones.clear()
        } else Log.d(TAG, "Cannot remove geofence location requests. Geofence provider is not set.")
    }

    private fun requestLocationUpdates() {
        if (!checkPermission(latestContext, backgroundLocation.id)) {
            Log.w(TAG, "Not registering for location updates because of permissions.")
            return
        }
        Log.d(TAG, "Registering for location updates.")

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(latestContext)
        val intent = getLocationUpdateIntent(false)

        fusedLocationProviderClient?.requestLocationUpdates(
            createLocationRequest(),
            intent
        )
    }

    private suspend fun requestZoneUpdates() {
        if (!checkPermission(latestContext, zoneLocation.id)) {
            Log.w(TAG, "Not registering for zone based updates because of permissions.")
            return
        }

        if (geofenceRegistered) {
            Log.w(TAG, "Not registering for zones as we already have")
            return
        }

        Log.d(TAG, "Registering for zone based location updates")

        try {
            geofencingClient = LocationServices.getGeofencingClient(latestContext)
            val intent = getLocationUpdateIntent(true)
            val geofencingRequest = createGeofencingRequest()
            geofencingClient?.addGeofences(
                geofencingRequest,
                intent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Issue requesting zone updates.", e)
        }
    }

    private fun handleLocationUpdate(intent: Intent) {
        Log.d(TAG, "Received location update.")
        LocationResult.extractResult(intent)?.lastLocation?.let { location ->
            val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
            val sensorSettings = sensorDao.getSettings(backgroundLocation.id)
            val minAccuracy = sensorSettings
                .firstOrNull { it.name == SETTING_ACCURACY }?.value?.toIntOrNull()
                ?: DEFAULT_MINIMUM_ACCURACY
            sensorDao.add(SensorSetting(backgroundLocation.id, SETTING_ACCURACY, minAccuracy.toString(), "number"))
            if (location.accuracy > minAccuracy) {
                Log.w(TAG, "Location accuracy didn't meet requirements, disregarding: $location")
            } else {
                // Update GeoLocation Sensor (if enabled) with new Location
                val geoSensorManager = SensorReceiver.MANAGERS.firstOrNull { it.getAvailableSensors(latestContext).any { s -> s.name == commonR.string.basic_sensor_name_geolocation } }
                if (geoSensorManager != null) {
                    if (geoSensorManager.isEnabled(latestContext, "geocoded_location")) {
                        geoSensorManager.requestSensorUpdate(latestContext)
                    } else {
                        HighAccuracyLocationService.updateNotificationAddress(latestContext, location)
                    }
                }

                // Send new location to Home Assistant
                sendLocationUpdate(location)
            }
        }
    }

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (DisabledLocationHandler.hasGPS(context)) {
            listOf(singleAccurateLocation, backgroundLocation, zoneLocation, highAccuracyMode)
        } else {
            listOf(backgroundLocation, zoneLocation, highAccuracyMode)
        }
    }

    private fun handleGeoUpdate(intent: Intent) {
        Log.d(TAG, "Received geofence update.")
        if (!isEnabled(latestContext, zoneLocation.id)) {
            isZoneLocationSetup = false
            Log.w(TAG, "Unregistering geofences as zone tracking is disabled and intent was received")
            removeGeofenceUpdateRequests()
            return
        }
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Error getting geofence broadcast status code: ${geofencingEvent.errorCode}")
            return
        }

        if (geofencingEvent.triggeringLocation == null) {
            Log.d(TAG, "Geofence event is null")
            return
        }

        val validGeofencingEvents = listOf(Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_EXIT)
        if (geofencingEvent.geofenceTransition in validGeofencingEvents) {
            val zoneStatusEvent = when (geofencingEvent.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "android.zone_entered"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "android.zone_exited"
                else -> ""
            }

            for (triggeringGeofence in geofencingEvent.triggeringGeofences) {
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
                    "accuracy" to geofencingEvent.triggeringLocation.accuracy,
                    "altitude" to geofencingEvent.triggeringLocation.altitude,
                    "bearing" to geofencingEvent.triggeringLocation.bearing,
                    "latitude" to geofencingEvent.triggeringLocation.latitude,
                    "longitude" to geofencingEvent.triggeringLocation.longitude,
                    "provider" to geofencingEvent.triggeringLocation.provider,
                    "time" to geofencingEvent.triggeringLocation.time,
                    "vertical_accuracy" to if (Build.VERSION.SDK_INT >= 26) geofencingEvent.triggeringLocation.verticalAccuracyMeters.toInt() else 0,
                    "zone" to zone
                )
                runBlocking {
                    try {
                        integrationUseCase.fireEvent(zoneStatusEvent, zoneAttr as Map<String, Any>)
                        Log.d(TAG, "Event sent to Home Assistant")
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to send event to Home Assistant", e)
                        Toast.makeText(latestContext, commonR.string.zone_event_failure, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }

        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val sensorSettings = sensorDao.getSettings(zoneLocation.id)
        val minAccuracy = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURACY }?.value?.toIntOrNull()
            ?: DEFAULT_MINIMUM_ACCURACY
        sensorDao.add(SensorSetting(zoneLocation.id, SETTING_ACCURACY, minAccuracy.toString(), "number"))

        if (geofencingEvent.triggeringLocation.accuracy > minAccuracy) {
            Log.w(
                TAG,
                "Geofence location accuracy didn't meet requirements, requesting new location."
            )
            requestSingleAccurateLocation()
        } else {
            sendLocationUpdate(geofencingEvent.triggeringLocation, true)
        }

        ioScope.launch {
            setupBackgroundLocation()
        }
    }

    private fun sendLocationUpdate(location: Location, geofenceUpdate: Boolean = false) {
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
        }
        val updateLocation = UpdateLocation(
            arrayOf(location.latitude, location.longitude),
            accuracy,
            location.speed.toInt(),
            location.altitude.toInt(),
            location.bearing.toInt(),
            if (Build.VERSION.SDK_INT >= 26) location.verticalAccuracyMeters.toInt() else 0
        )

        val now = System.currentTimeMillis()

        Log.d(TAG, "Begin evaluating if location update should be skipped")
        if (now + 5000 < location.time && !highAccuracyModeEnabled) {
            Log.d(TAG, "Skipping location update that came from the future. ${now + 5000} should always be greater than ${location.time}")
            return
        }

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
            if (lastUpdateLocation == updateLocation.gps.contentToString()) {
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
            Log.d(TAG, "Skipping location update due to old timestamp ${location.time} compared to $now")
            return
        }
        lastLocationSend = now
        lastUpdateLocation = updateLocation.gps.contentToString()

        ioScope.launch {
            try {
                integrationUseCase.updateLocation(updateLocation)
                Log.d(TAG, "Location update sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Could not update location.", e)
            }
        }
    }

    private fun getLocationUpdateIntent(isGeofence: Boolean): PendingIntent {
        val intent = Intent(latestContext, LocationSensorManager::class.java)
        intent.action = if (isGeofence) ACTION_PROCESS_GEO else ACTION_PROCESS_LOCATION
        return PendingIntent.getBroadcast(latestContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    private fun createLocationRequest(): LocationRequest {
        val locationRequest = LocationRequest()

        locationRequest.interval = 60000 // Every 60 seconds
        locationRequest.fastestInterval = 30000 // Every 30 seconds
        locationRequest.maxWaitTime = 200000 // Every 5 minutes

        locationRequest.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY

        return locationRequest
    }

    private suspend fun createGeofencingRequest(): GeofencingRequest {
        val geofencingRequestBuilder = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)

        // TODO cache the zones on device so we don't need to reach out each time
        var configuredZones: Array<Entity<ZoneAttributes>> = emptyArray()
        try {
            configuredZones = integrationUseCase.getZones()
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving zones from Home Assistant", e)
        }

        val highAccuracyTriggerRange = getHighAccuracyModeTriggerRange()
        val highAccuracyZones = getHighAccuracyModeZones(false)
        configuredZones.forEach {
            addGeofenceToBuilder(geofencingRequestBuilder, it)
            if (highAccuracyTriggerRange > 0 && highAccuracyZones.contains(it.entityId)) {
                addGeofenceToBuilder(geofencingRequestBuilder, it, highAccuracyTriggerRange)
            }
        }

        geofenceRegistered = true
        return geofencingRequestBuilder.build()
    }

    private fun addGeofenceToBuilder(
        geofencingRequestBuilder: GeofencingRequest.Builder,
        zone: Entity<ZoneAttributes>,
        triggerRange: Int = 0
    ) {
        val postRequestId = if (triggerRange > 0)"_expanded" else ""
        geofencingRequestBuilder
            .addGeofence(
                Geofence.Builder()
                    .setRequestId(zone.entityId + postRequestId)
                    .setCircularRegion(
                        zone.attributes.latitude,
                        zone.attributes.longitude,
                        zone.attributes.radius + triggerRange
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            )
    }

    private fun getHighAccuracyModeTriggerRange(): Int {
        val enabled = isEnabled(latestContext, zoneLocation.id)

        if (!enabled) return 0

        val highAccuracyTriggerRange = getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE,
            "number",
            DEFAULT_TRIGGER_RANGE_METERS.toString()
        )

        var highAccuracyTriggerRangeInt = highAccuracyTriggerRange.toIntOrNull() ?: DEFAULT_TRIGGER_RANGE_METERS
        if (highAccuracyTriggerRangeInt < 0) {
            highAccuracyTriggerRangeInt = DEFAULT_TRIGGER_RANGE_METERS

            val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
            sensorDao.add(SensorSetting(backgroundLocation.id, SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE, highAccuracyTriggerRangeInt.toString(), "number"))
        }

        return highAccuracyTriggerRangeInt
    }

    private fun getHighAccuracyModeZones(expandedZones: Boolean): List<String> {
        val enabled = isEnabled(latestContext, zoneLocation.id)

        if (!enabled) return emptyList()

        val highAccuracyZones = getSetting(
            latestContext,
            backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_ZONE,
            "list-zones",
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
        if (!isEnabled(latestContext, singleAccurateLocation.id)) {
            Log.w(TAG, "Requested single accurate location but it is not enabled.")
            return
        }

        val now = System.currentTimeMillis()
        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val fullSensor = sensorDao.getFull(singleAccurateLocation.id)
        val latestAccurateLocation = fullSensor?.attributes?.firstOrNull { it.name == "lastAccurateLocationRequest" }?.value?.toLongOrNull() ?: 0L

        val sensorSettings = sensorDao.getSettings(singleAccurateLocation.id)
        val minAccuracy = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURACY }?.value?.toIntOrNull()
            ?: DEFAULT_MINIMUM_ACCURACY
        sensorDao.add(SensorSetting(singleAccurateLocation.id, SETTING_ACCURACY, minAccuracy.toString(), "number"))
        val minTimeBetweenUpdates = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURATE_UPDATE_TIME }?.value?.toIntOrNull()
            ?: 60000
        sensorDao.add(SensorSetting(singleAccurateLocation.id, SETTING_ACCURATE_UPDATE_TIME, minTimeBetweenUpdates.toString(), "number"))

        // Only update accurate location at most once a minute
        if (now < latestAccurateLocation + minTimeBetweenUpdates) {
            Log.d(TAG, "Not requesting accurate location, last accurate location was too recent")
            return
        }
        sensorDao.add(Attribute(singleAccurateLocation.id, "lastAccurateLocationRequest", now.toString(), "string"))

        val maxRetries = 5
        val request = createLocationRequest().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = maxRetries
            interval = 10000
            fastestInterval = 5000
        }
        LocationServices.getFusedLocationProviderClient(latestContext)
            .requestLocationUpdates(
                request,
                object : LocationCallback() {
                    val wakeLock: PowerManager.WakeLock? =
                        latestContext.getSystemService<PowerManager>()
                            ?.newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK,
                                "HomeAssistant::AccurateLocation"
                            )?.apply { acquire(10 * 60 * 1000L /*10 minutes*/) }
                    var numberCalls = 0
                    override fun onLocationResult(locationResult: LocationResult) {
                        numberCalls++
                        Log.d(
                            TAG,
                            "Got single accurate location update: ${locationResult.lastLocation}"
                        )
                        if (locationResult.equals(null)) {
                            Log.w(TAG, "No location provided.")
                            return
                        }

                        when {
                            locationResult.lastLocation.accuracy <= minAccuracy -> {
                                Log.d(TAG, "Location accurate enough, all done with high accuracy.")
                                runBlocking { sendLocationUpdate(locationResult.lastLocation) }
                                if (wakeLock?.isHeld == true) wakeLock.release()
                            }
                            numberCalls >= maxRetries -> {
                                Log.d(
                                    TAG,
                                    "No location was accurate enough, sending our last location anyway"
                                )
                                if (locationResult.lastLocation.accuracy <= minAccuracy * 2)
                                    runBlocking { sendLocationUpdate(locationResult.lastLocation) }
                                if (wakeLock?.isHeld == true) wakeLock.release()
                            }
                            else -> {
                                Log.w(
                                    TAG,
                                    "Location not accurate enough on retry $numberCalls of $maxRetries"
                                )
                            }
                        }
                    }
                },
                Looper.getMainLooper()
            )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/location"
    }
    override val enabledByDefault: Boolean
        get() = false

    override val name: Int
        get() = commonR.string.sensor_name_location

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH,
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
        if (isEnabled(context, zoneLocation.id) || isEnabled(context, backgroundLocation.id))
            setupLocationTracking()
        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val sensorSetting = sensorDao.getSettings(singleAccurateLocation.id)
        val includeSensorUpdate = sensorSetting.firstOrNull { it.name == SETTING_INCLUDE_SENSOR_UPDATE }?.value ?: "false"
        if (includeSensorUpdate == "true") {
            if (isEnabled(context, singleAccurateLocation.id)) {
                context.sendBroadcast(
                    Intent(context, this.javaClass).apply {
                        action = ACTION_REQUEST_ACCURATE_LOCATION_UPDATE
                    }
                )
            }
        } else
            sensorDao.add(SensorSetting(singleAccurateLocation.id, SETTING_INCLUDE_SENSOR_UPDATE, "false", "toggle"))
    }
}
