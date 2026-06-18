package io.homeassistant.companion.android.common.sensors

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.BuildConfig
import javax.inject.Inject

@AndroidEntryPoint
class SensorUpdateReceiver : SensorReceiverBase() {

    @Inject
    lateinit var bluetoothSensorManager: BluetoothSensorManager

    companion object {
        fun updateSensors(context: Context) {
            val intent = Intent(context, SensorUpdateReceiver::class.java)
            intent.action = ACTION_UPDATE_SENSORS
            context.sendBroadcast(intent)
        }
    }

    override val currentAppVersion: String
        get() = BuildConfig.VERSION_NAME

    override val managers: Set<SensorManager>
        get() = setOf(bluetoothSensorManager)

    override val skippableActions: Map<String, List<String>>
        get() = emptyMap()

    override fun getSensorSettingsIntent(
        context: Context,
        sensorId: String,
        sensorManagerId: String,
        notificationId: Int,
    ): PendingIntent? {
        return null
    }
}
