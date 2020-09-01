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
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.UpdateLocation
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LocationSensorManager : BroadcastReceiver(), SensorManager {

    companion object {
        const val MINIMUM_ACCURACY = 200

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
        internal const val TAG = "LocBroadcastReceiver"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    lateinit var latestContext: Context

    private var isBackgroundLocationSetup = false
    private var isZoneLocationSetup = false

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
        if (!checkPermission(latestContext)) {
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
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(latestContext)
        val backgroundIntent = getLocationUpdateIntent(false)

        fusedLocationProviderClient.removeLocationUpdates(backgroundIntent)

        val geofencingClient = LocationServices.getGeofencingClient(latestContext)
        val zoneIntent = getLocationUpdateIntent(true)
        geofencingClient.removeGeofences(zoneIntent)
    }

    private fun requestLocationUpdates() {
        if (!checkPermission(latestContext)) {
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
        if (!checkPermission(latestContext)) {
            Log.w(TAG, "Not registering for zone based updates because of permissions.")
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
        LocationResult.extractResult(intent)?.lastLocation?.let {
            if (it.accuracy > MINIMUM_ACCURACY) {
                Log.w(TAG, "Location accuracy didn't meet requirements, disregarding: $it")
            } else {
                sendLocationUpdate(it)
            }
        }
    }

    private fun handleGeoUpdate(intent: Intent) {
        Log.d(TAG, "Received geofence update.")
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Error getting geofence broadcast status code: ${geofencingEvent.errorCode}")
            return
        }

        if (geofencingEvent.triggeringLocation.accuracy > MINIMUM_ACCURACY) {
            Log.w(
                TAG,
                "Geofence location accuracy didn't meet requirements, requesting new location."
            )
            requestSingleAccurateLocation()
        } else {
            sendLocationUpdate(geofencingEvent.triggeringLocation)
        }
    }

    private fun sendLocationUpdate(location: Location) {
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
            "",
            arrayOf(location.latitude, location.longitude),
            accuracy,
            location.speed.toInt(),
            location.altitude.toInt(),
            location.bearing.toInt(),
            if (Build.VERSION.SDK_INT >= 26) location.verticalAccuracyMeters.toInt() else 0
        )

        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val fullSensor = sensorDao.getFull(backgroundLocation.id)
        val locationEntity = fullSensor?.sensor
        val lastLocationSend = fullSensor?.attributes?.firstOrNull { it.name == "lastLocationSent" }?.value?.toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()

        if (locationEntity?.state == updateLocation.gps.contentToString()) {
            if (now >= lastLocationSend + 900000) {
                Log.d(TAG, "Sending location since it's been more than 15 minutes")
            } else {
                Log.d(TAG, "Same location as last update, not sending to HA")
                return
            }
        }

        ioScope.launch {
            try {
                integrationUseCase.updateLocation(updateLocation)
                onSensorUpdated(
                    latestContext,
                    backgroundLocation,
                    updateLocation.gps.contentToString(),
                    "",
                    mapOf(
                        "lastLocationSent" to now.toString()
                    )
                )
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
        return geofencingRequestBuilder.build()
    }

    private fun requestSingleAccurateLocation() {
        if (!checkPermission(latestContext)) {
            Log.w(TAG, "Not getting single accurate location because of permissions.")
            return
        }
        val now = System.currentTimeMillis()
        val sensorDao = AppDatabase.getInstance(latestContext).sensorDao()
        val fullSensor = sensorDao.getFull(backgroundLocation.id)
        val latestAccurateLocation = fullSensor?.attributes?.firstOrNull { it.name == "lastAccurateLocationRequest" }?.value?.toLongOrNull() ?: 0L
        // Only update accurate location at most once a minute
        if (now < latestAccurateLocation + 60000) {
            Log.d(TAG, "Not requesting accurate location, last accurate location was too recent")
            return
        }
        sensorDao.add(Attribute(backgroundLocation.id, "lastAccurateLocationRequest", now.toString()))

        val maxRetries = 5
        val request = createLocationRequest()
        request.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        request.numUpdates = maxRetries
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
                            locationResult.lastLocation.accuracy <= MINIMUM_ACCURACY -> {
                                Log.d(TAG, "Location accurate enough, all done with high accuracy.")
                                runBlocking { sendLocationUpdate(locationResult.lastLocation) }
                                if (wakeLock?.isHeld == true) wakeLock.release()
                            }
                            numberCalls >= maxRetries -> {
                                Log.d(
                                    TAG,
                                    "No location was accurate enough, sending our last location anyway"
                                )
                                if (locationResult.lastLocation.accuracy <= MINIMUM_ACCURACY * 2)
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

    override val name: Int
        get() = R.string.sensor_name_location

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(backgroundLocation, zoneLocation)

    override fun requiredPermissions(): Array<String> {
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
        if (isEnabled(context, backgroundLocation.id)) {
            context.sendBroadcast(
                Intent(context, this.javaClass).apply {
                    action = ACTION_REQUEST_ACCURATE_LOCATION_UPDATE
                }
            )
        }
    }
}
