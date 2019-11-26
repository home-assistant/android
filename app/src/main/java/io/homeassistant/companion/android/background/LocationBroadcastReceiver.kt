package io.homeassistant.companion.android.background

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

class LocationBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REQUEST_LOCATION_UPDATES =
            "io.homeassistant.companion.android.background.REQUEST_UPDATES"
        const val ACTION_PROCESS_LOCATION =
            "io.homeassistant.companion.android.background.PROCESS_UPDATES"

        private const val TAG = "LocBroadcastReceiver"
    }


    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> requestUpdates(context, intent)
            ACTION_REQUEST_LOCATION_UPDATES -> requestUpdates(context, intent)
            ACTION_PROCESS_LOCATION -> handleUpdate(context, intent)
            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun requestUpdates(context: Context, intent: Intent) {
        Log.d(TAG, "Registering for location updates.")

        if (ActivityCompat.checkSelfPermission(
                context!!,
                ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Not starting location reporting because of permissions.")
            return
        }

        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        fusedLocationProviderClient.requestLocationUpdates(
            createLocationRequest(),
            getLocationUpdateIntent(context)
        )

    }

    private fun handleUpdate(context: Context, intent: Intent) {
        Log.d(TAG, "Received location update.")
        val locationResult = LocationResult.extractResult(intent)
        val lastLocation = locationResult.lastLocation
        if (lastLocation != null) {
            Log.d(
                TAG, "Last Location: " +
                        "\nCoords:(${lastLocation.latitude}, ${lastLocation.longitude})" +
                        "\nAccuracy: ${lastLocation.accuracy}" +
                        "\nBearing: ${lastLocation.bearing}"
            )

            // Call service with location
        }
    }


    private fun getLocationUpdateIntent(context: Context): PendingIntent {
        val intent = Intent(context, LocationBroadcastReceiver::class.java)
        intent.action = ACTION_PROCESS_LOCATION
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

}
