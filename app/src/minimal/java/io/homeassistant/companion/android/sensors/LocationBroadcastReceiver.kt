package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class LocationBroadcastReceiver : BroadcastReceiver(), SensorManager {

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
            "Background Location"
        )
        val zoneLocation = SensorManager.BasicSensor(
            "zone_background",
            "",
            "Zone Location"
        )
        internal const val TAG = "LocBroadcastReceiver"

        fun restartLocationTracking(context: Context) {
            // Noop
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Noop
    }

    override val name: String
        get() = "Location Sensors"
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf()

    override fun requiredPermissions(): Array<String> {
        // Noop
        return emptyArray()
    }

    override fun getSensorData(context: Context, sensorId: String): SensorRegistration<Any> {
        TODO("Not yet implemented")
    }
}
