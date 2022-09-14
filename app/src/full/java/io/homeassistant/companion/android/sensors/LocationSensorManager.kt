package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.GCJ2WGS
import io.homeassistant.companion.android.common.data.integration.UpdateLocation
import io.homeassistant.companion.android.common.data.integration.ZoneAttributes
import io.homeassistant.companion.android.common.sensors.LocationSensorManagerBase
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.location.HighAccuracyLocationService
import io.homeassistant.companion.android.notifications.MessagingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.homeassistant.companion.android.common.R as commonR
import com.amap.api.location.*
import io.homeassistant.companion.android.common.bluetooth.BluetoothUtils

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
        private const val SETTING_HIGH_ACCURACY_BT_ZONE_COMBINED = "location_ham_zone_bt_combined"
        private const val SETTING_HIGH_ACCURACY_MODE_TRIGGER_RANGE_ZONE = "location_ham_trigger_range"

        private const val DEFAULT_MINIMUM_ACCURACY = 200
        private const val DEFAULT_UPDATE_INTERVAL_HA_SECONDS = 5
        private const val DEFAULT_TRIGGER_RANGE_METERS = 300

        private const val DEFAULT_LOCATION_INTERVAL: Long = 60000
        private const val DEFAULT_LOCATION_FAST_INTERVAL: Long = 30000
        private const val DEFAULT_LOCATION_MAX_WAIT_TIME: Long = 200000

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

        private var geofenceRegistered = false

        private var lastHighAccuracyMode = false
        private var lastHighAccuracyUpdateInterval = DEFAULT_UPDATE_INTERVAL_HA_SECONDS
        private var forceHighAccuracyModeOn = false
        private var highAccuracyModeEnabled = false


        fun setHighAccuracyModeSetting(context: Context, enabled: Boolean) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            sensorDao.add(SensorSetting(backgroundLocation.id, SETTING_HIGH_ACCURACY_MODE, enabled.toString(), SensorSettingType.TOGGLE))
        }

        fun getHighAccuracyModeIntervalSetting(context: Context): Int {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            val sensorSettings = sensorDao.getSettings(backgroundLocation.id)
            return sensorSettings.firstOrNull { it.name == SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL }?.value?.toIntOrNull() ?: DEFAULT_UPDATE_INTERVAL_HA_SECONDS
        }

        fun setHighAccuracyModeIntervalSetting(context: Context, updateInterval: Int) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            sensorDao.add(SensorSetting(backgroundLocation.id, SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL, updateInterval.toString(), SensorSettingType.NUMBER))
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
                sendLocationUpdate(location)
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
                    MessagingManager.TURN_ON, MessagingManager.TURN_OFF -> {
                        var turnOn = command == MessagingManager.TURN_ON
                        if (turnOn) Log.d(TAG, "Forcing of high accuracy mode enabled")
                        else Log.d(TAG, "Forcing of high accuracy mode disabled")
                        forceHighAccuracyModeOn = turnOn
                        setHighAccuracyModeSetting(latestContext, turnOn)
                        ioScope.launch {
                            handleLocationUpdate(intent)
                        }
                    }

                    MessagingManager.HIGH_ACCURACY_SET_UPDATE_INTERVAL -> {
                        if (lastHighAccuracyMode)
                            handleLocationUpdate(intent)
                    }
                }
            }
            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun setupLocationTracking() {
        Log.w(TAG, "setupLocationTracking")
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
                    isZoneLocationSetup = false
                    Log.d(TAG, "Removing geofence update requests")
                }
                if (!backgroundEnabled && isBackgroundLocationSetup) {
                    removeBackgroundUpdateRequests()
                    isBackgroundLocationSetup = false
                    Log.d(TAG, "Removing background update requests")
                }
                if (backgroundEnabled) {

                    val updateIntervalHighAccuracySeconds = getHighAccuracyModeUpdateInterval()
                    val highAccuracyMode = getHighAccuracyModeState()

                    lastHighAccuracyMode = highAccuracyMode
                    lastHighAccuracyUpdateInterval = updateIntervalHighAccuracySeconds

                    if (!isBackgroundLocationSetup) {
                        isBackgroundLocationSetup = true
                        requestLocationUpdates()
                    } else {
                        requestLocationUpdates()
                    }

                }
                if (zoneEnabled && !isZoneLocationSetup) {
                    isZoneLocationSetup = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Issue setting up location tracking", e)
            }
        }
    }

    private fun getHighAccuracyModeUpdateInterval(): Int {

        val updateIntervalHighAccuracySeconds = getSetting(
            latestContext,
            LocationSensorManager.backgroundLocation,
            SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL,
            SensorSettingType.NUMBER,
            DEFAULT_UPDATE_INTERVAL_HA_SECONDS.toString()
        )

        var updateIntervalHighAccuracySecondsInt = updateIntervalHighAccuracySeconds.toInt()
        if (updateIntervalHighAccuracySecondsInt < 5) {
            updateIntervalHighAccuracySecondsInt = DEFAULT_UPDATE_INTERVAL_HA_SECONDS

            val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
            sensorDao.add(
                SensorSetting(
                    backgroundLocation.id,
                    SETTING_HIGH_ACCURACY_MODE_UPDATE_INTERVAL,
                    updateIntervalHighAccuracySecondsInt.toString(),
                    SensorSettingType.NUMBER
                )
            )
        }
        return updateIntervalHighAccuracySecondsInt
    }

    private fun getHighAccuracyModeState(): Boolean {

        return getHighAccuracyModeSetting()

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


    private fun removeAllLocationUpdateRequests() {
        Log.d(TAG, "Removing all location requests.")
        removeBackgroundUpdateRequests()
    }

    private fun removeBackgroundUpdateRequests() {
        mLocationClient!!.stopLocation()
    }


    private fun requestLocationUpdates() {
        if (!checkPermission(latestContext, backgroundLocation.id)) {
            Log.w(TAG, "Not registering for location updates because of permissions.")
            return
        }

        AMapLocationClient.updatePrivacyShow(latestContext, true, true);
        AMapLocationClient.updatePrivacyAgree(latestContext, true);

        mLocationClient = AMapLocationClient(latestContext)

//        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
//        val sensorSettings = sensorDao.getSettings(singleAccurateLocation.id)
//        val minTimeBetweenUpdates = sensorSettings
//            .firstOrNull { it.name == SETTING_ACCURATE_UPDATE_TIME }?.value?.toIntOrNull()
//            ?: 1000 * 60 * 5
//        Log.d(TAG, "Registering for location updates.")
        mLocationClient!!.setLocationListener(mLocationListener)
        val mLocationOption = AMapLocationClientOption()

        if (lastHighAccuracyMode) {
            mLocationOption.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        } else {
            mLocationOption.locationMode = AMapLocationClientOption.AMapLocationMode.Battery_Saving
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

    private fun handleLocationUpdate(intent: Intent) {
        if (mLocationClient != null) {
            mLocationClient!!.startLocation()
        } else {
            requestLocationUpdates()
        }

    }

    private fun sendLocationUpdate(
        location: Location?,
        name: String = "",
        geofenceUpdate: Boolean = false
    ) {
        if (location == null) return

        Log.d(
            TAG, "Last Location: " +
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

                // Update Geocoded Location Sensor
                val intent = Intent(latestContext, SensorReceiver::class.java)
                intent.action = SensorReceiverBase.ACTION_UPDATE_SENSOR
                intent.putExtra(SensorReceiverBase.EXTRA_SENSOR_ID, GeocodeSensorManager.geocodedLocation.id)
                latestContext.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not update location.", e)
            }
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
        sensorDao.add(SensorSetting(singleAccurateLocation.id, SETTING_ACCURACY, minAccuracy.toString(), SensorSettingType.NUMBER))
        val minTimeBetweenUpdates = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURATE_UPDATE_TIME }?.value?.toIntOrNull()
            ?: 60000
        sensorDao.add(SensorSetting(singleAccurateLocation.id, SETTING_ACCURATE_UPDATE_TIME, minTimeBetweenUpdates.toString(), SensorSettingType.NUMBER))

        // Only update accurate location at most once a minute
        if (now < latestAccurateLocation + minTimeBetweenUpdates) {
            Log.d(TAG, "Not requesting accurate location, last accurate location was too recent")
            return
        }
        sensorDao.add(Attribute(singleAccurateLocation.id, "lastAccurateLocationRequest", now.toString(), "string"))

        val wakeLock: PowerManager.WakeLock? =
            ContextCompat.getSystemService(latestContext, PowerManager::class.java)
                ?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "HomeAssistant::AccurateLocation"
                )?.apply { acquire(10 * 60 * 1000L /*10 minutes*/) }

        runBlocking { sendLocationUpdate(amapLocation) }
        if (wakeLock?.isHeld == true) wakeLock.release()

        if (mLocationClient != null) {
            mLocationClient!!.startLocation()
        } else {
            requestLocationUpdates()
        }
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
            sensorDao.add(SensorSetting(singleAccurateLocation.id, SETTING_INCLUDE_SENSOR_UPDATE, "false", SensorSettingType.TOGGLE))
    }

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (DisabledLocationHandler.hasGPS(context)) {
            listOf(singleAccurateLocation, backgroundLocation, zoneLocation, highAccuracyMode, highAccuracyUpdateInterval)
        } else {
            listOf(backgroundLocation, zoneLocation, highAccuracyMode, highAccuracyUpdateInterval)
        }
    }
}
