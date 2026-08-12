package io.homeassistant.companion.android.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.launchAsync
import io.homeassistant.companion.android.sensors.LocationSensorManager
import io.homeassistant.companion.android.sensors.LocationSensorManager.Companion.setHighAccuracyModeSetting
import io.homeassistant.companion.android.sensors.LocationSensorReceiver
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

@AndroidEntryPoint
class HighAccuracyLocationReceiver : BroadcastReceiver() {
    companion object {
        const val HIGH_ACCURACY_LOCATION_DISABLE = "DISABLE_HIGH_ACCURACY_MODE"
    }

    @Inject
    lateinit var sensorRepository: SensorRepository

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HIGH_ACCURACY_LOCATION_DISABLE -> {
                HighAccuracyLocationService.stopService(context)
                launchAsync(ioScope) {
                    sensorRepository.setHighAccuracyModeSetting(false)
                    val locationIntent = Intent(context, LocationSensorReceiver::class.java)
                    locationIntent.action = LocationSensorManager.ACTION_REQUEST_LOCATION_UPDATES
                    context.sendBroadcast(locationIntent)
                }
            }
        }
    }
}
