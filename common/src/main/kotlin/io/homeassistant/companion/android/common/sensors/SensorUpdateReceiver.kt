package io.homeassistant.companion.android.common.sensors

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.BuildConfig

@AndroidEntryPoint
class SensorUpdateReceiver : SensorReceiverBase() {

    companion object {
        fun updateSensors(context: Context) {
            val intent = Intent(context, SensorUpdateReceiver::class.java)
            intent.action = ACTION_UPDATE_SENSORS
            context.sendBroadcast(intent)
        }
    }

    override val currentAppVersion: String
        get() = BuildConfig.VERSION_NAME

    override val managers: List<SensorManager>
        get() = listOf(BluetoothSensorManager())

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
