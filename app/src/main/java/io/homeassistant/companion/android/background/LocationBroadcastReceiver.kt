package io.homeassistant.companion.android.background

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.UpdateLocation
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LocationBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REQUEST_LOCATION_UPDATES =
            "io.homeassistant.companion.android.background.REQUEST_UPDATES"
        const val ACTION_PROCESS_LOCATION =
            "io.homeassistant.companion.android.background.PROCESS_UPDATES"

        private const val TAG = "LocBroadcastReceiver"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onReceive(context: Context, intent: Intent) {
        ensureInjected(context)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> requestUpdates(context)
            ACTION_REQUEST_LOCATION_UPDATES -> requestUpdates(context)
            ACTION_PROCESS_LOCATION -> handleUpdate(context, intent)
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

    private fun requestUpdates(context: Context) {
        Log.d(TAG, "Registering for location updates.")

        if (ActivityCompat.checkSelfPermission(
                context,
                ACCESS_COARSE_LOCATION
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
        LocationResult.extractResult(intent)?.lastLocation?.let {

            Log.d(
                TAG, "Last Location: " +
                    "\nCoords:(${it.latitude}, ${it.longitude})" +
                    "\nAccuracy: ${it.accuracy}" +
                    "\nBearing: ${it.bearing}"
            )
            val updateLocation = UpdateLocation(
                "",
                arrayOf(it.latitude, it.longitude),
                it.accuracy.toInt(),
                getBatteryLevel(context),
                it.speed.toInt(),
                it.altitude.toInt(),
                it.bearing.toInt(),
                if (Build.VERSION.SDK_INT >= 26) it.verticalAccuracyMeters.toInt() else null
            )

            mainScope.launch {
                try {
                    integrationUseCase.updateLocation(updateLocation)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not update location.", e)
                }
            }
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

    private fun getBatteryLevel(context: Context): Int? {
        val batteryIntent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level == -1 || scale == -1) {
            Log.e(TAG, "Issue getting battery level!")
            return null
        }

        return (level.toFloat() / scale.toFloat() * 100.0f).toInt()
    }
}
