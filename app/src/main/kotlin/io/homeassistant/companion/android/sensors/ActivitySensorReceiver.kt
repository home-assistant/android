package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Thin [BroadcastReceiver] that forwards activity-recognition and sleep broadcasts to the [ActivitySensorManager],
 * which owns the sensor logic.
 */
@AndroidEntryPoint
class ActivitySensorReceiver : BroadcastReceiver() {

    @Inject
    lateinit var activitySensorManager: ActivitySensorManager

    override fun onReceive(context: Context, intent: Intent) {
        activitySensorManager.onReceive(intent)
    }
}
