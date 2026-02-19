package io.homeassistant.companion.android.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.sensors.LocationSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HighAccuracyLocationReceiver : BroadcastReceiver() {
    companion object {
        const val HIGH_ACCURACY_LOCATION_DISABLE = "DISABLE_HIGH_ACCURACY_MODE"
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HIGH_ACCURACY_LOCATION_DISABLE ->
                {
                    HighAccuracyLocationService.stopService(context)
                    ioScope.launch {
                        LocationSensorManager.setHighAccuracyModeSetting(context, false)
                        val locationIntent = Intent(context, LocationSensorManager::class.java)
                        locationIntent.action = LocationSensorManager.ACTION_REQUEST_LOCATION_UPDATES
                        context.sendBroadcast(locationIntent)
                    }
                }
        }
    }
}
