package io.homeassistant.companion.android.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Setting
import io.homeassistant.companion.android.sensors.LocationSensorManager

class HighAccuracyLocationReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "HighAccuracyLocationReceiver"
        const val HIGH_ACCURACY_LOCATION_DISABLE = "DISABLE_HIGH_ACCURACY_MODE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HIGH_ACCURACY_LOCATION_DISABLE ->
            {
                HighAccuracyLocationService.stopService(context)
                val sensorDao = AppDatabase.getInstance(context).sensorDao()
                sensorDao.add(Setting(LocationSensorManager.backgroundLocation.id, LocationSensorManager.SETTING_HIGH_ACCURACY_MODE, "false", "toggle"))
            }
        }
    }
}
