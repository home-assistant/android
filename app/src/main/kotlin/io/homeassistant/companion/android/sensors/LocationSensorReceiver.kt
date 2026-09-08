package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Thin [BroadcastReceiver] that forwards location/geofence broadcasts to the [LocationSensorManager],
 * which owns the sensor logic.
 */
@AndroidEntryPoint
class LocationSensorReceiver : BroadcastReceiver() {

    @Inject
    lateinit var locationSensorManager: LocationSensorManager

    override fun onReceive(context: Context, intent: Intent) {
        locationSensorManager.onReceive(intent)
    }
}
