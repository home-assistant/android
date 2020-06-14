package io.homeassistant.companion.android.background

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.UpdateLocation
import io.homeassistant.companion.android.util.PermissionManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LocationBroadcastReceiver : BroadcastReceiver() {

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

        private const val TAG = "LocBroadcastReceiver"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onReceive(context: Context, intent: Intent) {
        ensureInjected(context)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_REQUEST_LOCATION_UPDATES -> setupLocationTracking(context)
            ACTION_PROCESS_LOCATION -> handleLocationUpdate(intent)
            ACTION_PROCESS_GEO -> handleGeoUpdate(context, intent)
            ACTION_REQUEST_ACCURATE_LOCATION_UPDATE -> requestSingleAccurateLocation(context)
            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerReceiverComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }

    private fun setupLocationTracking(context: Context) {
        if (!PermissionManager.checkLocationPermission(context)) {
            Log.w(TAG, "Not starting location reporting because of permissions.")
            return
        }

        mainScope.launch {
            try {
                removeAllLocationUpdateRequests(context)

                if (integrationUseCase.isBackgroundTrackingEnabled())
                    requestLocationUpdates(context)
                if (integrationUseCase.isZoneTrackingEnabled())
                    requestZoneUpdates(context)
            } catch (e: Exception) {
                Log.e(TAG, "Issue setting up location tracking", e)
            }
        }
    }

    private fun removeAllLocationUpdateRequests(context: Context) {
        Log.d(TAG, "Removing all location requests.")
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val backgroundIntent = getLocationUpdateIntent(context, false)

        fusedLocationProviderClient.removeLocationUpdates(backgroundIntent)

        val geofencingClient = LocationServices.getGeofencingClient(context)
        val zoneIntent = getLocationUpdateIntent(context, true)
        geofencingClient.removeGeofences(zoneIntent)
    }

    private fun requestLocationUpdates(context: Context) {
        if (!PermissionManager.checkLocationPermission(context)) {
            Log.w(TAG, "Not registering for location updates because of permissions.")
            return
        }
        Log.d(TAG, "Registering for location updates.")

        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val intent = getLocationUpdateIntent(context, false)

        fusedLocationProviderClient.requestLocationUpdates(
            createLocationRequest(),
            intent
        )
    }

    private suspend fun requestZoneUpdates(context: Context) {
        if (!PermissionManager.checkLocationPermission(context)) {
            Log.w(TAG, "Not registering for zone based updates because of permissions.")
            return
        }

        Log.d(TAG, "Registering for zone based location updates")

        try {
            val geofencingClient = LocationServices.getGeofencingClient(context)
            val intent = getLocationUpdateIntent(context, true)
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

    private fun handleGeoUpdate(context: Context, intent: Intent) {
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
            requestSingleAccurateLocation(context)
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
        val updateLocation = UpdateLocation(
            "",
            arrayOf(location.latitude, location.longitude),
            location.accuracy.toInt(),
            location.speed.toInt(),
            location.altitude.toInt(),
            location.bearing.toInt(),
            if (Build.VERSION.SDK_INT >= 26) location.verticalAccuracyMeters.toInt() else 0
        )

        mainScope.launch {
            try {
                integrationUseCase.updateLocation(updateLocation)
            } catch (e: Exception) {
                Log.e(TAG, "Could not update location.", e)
            }
        }
    }

    private fun getLocationUpdateIntent(context: Context, isGeofence: Boolean): PendingIntent {
        val intent = Intent(context, LocationBroadcastReceiver::class.java)
        intent.action = if (isGeofence) ACTION_PROCESS_GEO else ACTION_PROCESS_LOCATION
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
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

    private fun requestSingleAccurateLocation(context: Context) {
        if (!PermissionManager.checkLocationPermission(context)) {
            Log.w(TAG, "Not getting single accurate location because of permissions.")
            return
        }
        val maxRetries = 5
        val request = createLocationRequest()
        request.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        request.numUpdates = maxRetries
        LocationServices.getFusedLocationProviderClient(context)
            .requestLocationUpdates(
                request,
                object : LocationCallback() {
                    var numberCalls = 0
                    override fun onLocationResult(locationResult: LocationResult?) {
                        numberCalls++
                        Log.d(
                            TAG,
                            "Got single accurate location update: ${locationResult?.lastLocation}"
                        )
                        if (locationResult != null && locationResult.lastLocation.accuracy <= 1) {
                            Log.d(TAG, "Location accurate enough, all done with high accuracy.")
                            runBlocking { sendLocationUpdate(locationResult.lastLocation) }
                            LocationServices.getFusedLocationProviderClient(context)
                                .removeLocationUpdates(this)
                        } else if (numberCalls >= maxRetries) {
                            Log.d(
                                TAG,
                                "No location was accurate enough, sending our last location anyway"
                            )
                            runBlocking { sendLocationUpdate(locationResult!!.lastLocation) }
                        } else {
                            Log.w(
                                TAG,
                                "Location not accurate enough on retry $numberCalls of $maxRetries"
                            )
                        }
                    }
                },
                null
            )
    }
}
