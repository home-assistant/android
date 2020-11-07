package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.UpdateLocation
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Setting
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LocationSensorManager : BroadcastReceiver(), SensorManager {

    companion object {
        private const val SETTING_ACCURACY = "Minimum Accuracy"
        private const val SETTING_ACCURATE_UPDATE_TIME = "Minimum time between updates"
        private const val SETTING_INCLUDE_SENSOR_UPDATE = "Include in sensor update"

        private const val DEFAULT_MINIMUM_ACCURACY = 200

        const val ACTION_REQUEST_LOCATION_UPDATES =
            "io.homeassistant.companion.android.background.REQUEST_UPDATES"
        const val ACTION_REQUEST_ACCURATE_LOCATION_UPDATE =
            "io.homeassistant.companion.android.background.REQUEST_ACCURATE_UPDATE"
        const val ACTION_PROCESS_LOCATION =
            "io.homeassistant.companion.android.background.PROCESS_UPDATES"
        const val ACTION_PROCESS_GEO =
            "io.homeassistant.companion.android.background.PROCESS_GEOFENCE"

        val backgroundLocation = SensorManager.BasicSensor(
            "location_background",
            "",
            R.string.basic_sensor_name_location_background,
            R.string.sensor_description_location_background
        )
        val zoneLocation = SensorManager.BasicSensor(
            "zone_background",
            "",
            R.string.basic_sensor_name_location_zone,
            R.string.sensor_description_location_zone
        )
        val singleAccurateLocation = SensorManager.BasicSensor(
            "accurate_location",
            "",
            R.string.basic_sensor_name_location_accurate,
            R.string.sensor_description_location_accurate
        )
        internal const val TAG = "LocBroadcastReceiver"

        private var isBackgroundLocationSetup = false
        private var isZoneLocationSetup = false

        private var lastLocationSend = 0L
        private var lastUpdateLocation = ""

        private var geofenceRegistered = false
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    lateinit var latestContext: Context

    override fun onReceive(context: Context, intent: Intent) {
        latestContext = context
        ensureInjected()

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_REQUEST_LOCATION_UPDATES -> setupLocationTracking()
            ACTION_PROCESS_LOCATION -> handleLocationUpdate(intent)
            ACTION_PROCESS_GEO -> handleGeoUpdate(intent)
            ACTION_REQUEST_ACCURATE_LOCATION_UPDATE -> requestSingleAccurateLocation()
            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun ensureInjected() {
        if (latestContext.applicationContext is GraphComponentAccessor) {
            DaggerSensorComponent.builder()
                .appComponent((latestContext.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
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
                    Log.d(TAG, "Removing geofence update requests")
                }
                if (!backgroundEnabled && isBackgroundLocationSetup) {
                    removeBackgroundUpdateRequests()
                    isBackgroundLocationSetup = false
                    Log.d(TAG, "Removing background update requests")
                }
                if (backgroundEnabled && !isBackgroundLocationSetup) {
                    isBackgroundLocationSetup = true
                    requestLocationUpdates()
                }
                if (zoneEnabled && !isZoneLocationSetup) {
                    isZoneLocationSetup = true
                    requestZoneUpdates()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Issue setting up location tracking", e)
            }
        }
    }

    private fun removeAllLocationUpdateRequests() {
        Log.d(TAG, "Removing all location requests.")
        removeBackgroundUpdateRequests()
        removeGeofenceUpdateRequests()
    }

    private fun removeBackgroundUpdateRequests() {
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(latestContext)
        val backgroundIntent = getLocationUpdateIntent(false)

        fusedLocationProviderClient.removeLocationUpdates(backgroundIntent)
    }

    private fun removeGeofenceUpdateRequests() {
        val geofencingClient = LocationServices.getGeofencingClient(latestContext)
        val zoneIntent = getLocationUpdateIntent(true)
        geofencingClient.removeGeofences(zoneIntent)
        geofenceRegistered = false
    }

    private fun requestLocationUpdates() {
        if (!checkPermission(latestContext, backgroundLocation.id)) {
            Log.w(TAG, "Not registering for location updates because of permissions.")
            return
        }
        Log.d(TAG, "Registering for location updates.")

        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(latestContext)
        val intent = getLocationUpdateIntent(false)

        fusedLocationProviderClient.requestLocationUpdates(
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
            val geofencingClient = LocationServices.getGeofencingClient(latestContext)
            val intent = getLocationUpdateIntent(true)
            val geofencingRequest = createGeofencingRequest()
            geofencingClient.addGeofences(
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
            sensorDao.add(Setting(backgroundLocation.id, SETTING_ACCURACY, minAccuracy.toString(), "number"))
            if (location.accuracy > minAccuracy) {
                Log.w(TAG, "Location accuracy didn't meet requirements, disregarding: $location")
            } else {
                sendLocationUpdate(location)
            }
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

        val validGeofencingEvents = listOf(Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_EXIT)
        if (geofencingEvent.geofenceTransition in validGeofencingEvents) {
            val zoneStatusEvent = when (geofencingEvent.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "android.zone_entered"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "android.zone_exited"
                else -> ""
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
                "zone" to geofencingEvent.triggeringGeofences[0].requestId
            )
            runBlocking {
                try {
                    integrationUseCase.fireEvent(zoneStatusEvent, zoneAttr as Map<String, Any>)
                    Log.d(TAG, "Event sent to Home Assistant")
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to send event to Home Assistant", e)
                    Toast.makeText(latestContext, R.string.zone_event_failure, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }

        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val sensorSettings = sensorDao.getSettings(zoneLocation.id)
        val minAccuracy = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURACY }?.value?.toIntOrNull()
            ?: DEFAULT_MINIMUM_ACCURACY
        sensorDao.add(Setting(zoneLocation.id, SETTING_ACCURACY, minAccuracy.toString(), "number"))

        if (geofencingEvent.triggeringLocation.accuracy > minAccuracy) {
            Log.w(
                TAG,
                "Geofence location accuracy didn't meet requirements, requesting new location."
            )
            requestSingleAccurateLocation()
        } else {
            sendLocationUpdate(geofencingEvent.triggeringLocation, "", true)
        }
    }

    private fun sendLocationUpdate(location: Location, name: String = "", geofenceUpdate: Boolean = false) {
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
            name,
            arrayOf(location.latitude, location.longitude),
            accuracy,
            location.speed.toInt(),
            location.altitude.toInt(),
            location.bearing.toInt(),
            if (Build.VERSION.SDK_INT >= 26) location.verticalAccuracyMeters.toInt() else 0
        )

        val now = System.currentTimeMillis()

        Log.d(TAG, "Begin evaluating if location update should be skipped")
        if (now < location.time) {
            Log.d(TAG, "Skipping location update that came from the future. $now should always be greater than ${location.time}")
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
                if (now < lastLocationSend + 5000 && !geofenceUpdate) {
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
        return PendingIntent.getBroadcast(latestContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
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
        // TODO cache the zones on device so we don't need to reach out each time
        integrationUseCase.getZones().forEach {
            geofencingRequestBuilder.addGeofence(
                Geofence.Builder()
                    .setRequestId(it.entityId)
                    .setCircularRegion(
                        it.attributes.latitude,
                        it.attributes.longitude,
                        it.attributes.radius
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            )
        }
        geofenceRegistered = true
        return geofencingRequestBuilder.build()
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
        sensorDao.add(Setting(singleAccurateLocation.id, SETTING_ACCURACY, minAccuracy.toString(), "number"))
        val minTimeBetweenUpdates = sensorSettings
            .firstOrNull { it.name == SETTING_ACCURATE_UPDATE_TIME }?.value?.toIntOrNull()
            ?: 60000
        sensorDao.add(Setting(singleAccurateLocation.id, SETTING_ACCURATE_UPDATE_TIME, minTimeBetweenUpdates.toString(), "number"))

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
                        getSystemService(latestContext, PowerManager::class.java)
                            ?.newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK,
                                "HomeAssistant::AccurateLocation"
                            )?.apply { acquire(10 * 60 * 1000L /*10 minutes*/) }
                    var numberCalls = 0
                    override fun onLocationResult(locationResult: LocationResult?) {
                        numberCalls++
                        Log.d(
                            TAG,
                            "Got single accurate location update: ${locationResult?.lastLocation}"
                        )
                        if (locationResult == null) {
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
                null
            )
    }

    override val enabledByDefault: Boolean
        get() = true

    override val name: Int
        get() = R.string.sensor_name_location

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(singleAccurateLocation, backgroundLocation, zoneLocation)

    override fun requiredPermissions(sensorId: String): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        latestContext = context
        ensureInjected()
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
            sensorDao.add(Setting(singleAccurateLocation.id, SETTING_INCLUDE_SENSOR_UPDATE, "false", "toggle"))
    }
}
