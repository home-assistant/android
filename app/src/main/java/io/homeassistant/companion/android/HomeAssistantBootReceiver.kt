package io.homeassistant.companion.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.sensors.SensorWorker

/**
 * This class will receive a broadcast intent when the device boots up. This allows us to start the sensor worker
 * even if the user never starts the HomeAssistant app.
 */
class HomeAssistantBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SensorWorker.start(context)
    }
}
