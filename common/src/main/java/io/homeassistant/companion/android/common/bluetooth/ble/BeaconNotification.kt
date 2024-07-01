package io.homeassistant.companion.android.common.bluetooth.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.sensors.SensorUpdateReceiver
import io.homeassistant.companion.android.common.util.CHANNEL_BEACON_MONITOR
import io.homeassistant.companion.android.common.util.CHANNEL_BLE_TRANSMITTER

fun beaconNotification(isTransmitter: Boolean, context: Context): NotificationCompat.Builder {
    val builder = NotificationCompat.Builder(
        context,
        if (isTransmitter) {
            CHANNEL_BLE_TRANSMITTER
        } else {
            CHANNEL_BEACON_MONITOR
        }
    )
    builder.setSmallIcon(R.drawable.ic_stat_ic_notification)
    builder.setContentTitle(
        context.getString(
            if (isTransmitter) {
                R.string.beacon_transmitting
            } else {
                R.string.beacon_scanning
            }
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            if (isTransmitter) {
                CHANNEL_BLE_TRANSMITTER
            } else {
                CHANNEL_BEACON_MONITOR
            },
            context.getString(
                if (isTransmitter) {
                    R.string.beacon_transmitting
                } else {
                    R.string.beacon_scanning
                }
            ),
            NotificationManager.IMPORTANCE_LOW
        )
        val notifManager = context.getSystemService<NotificationManager>()!!
        notifManager.createNotificationChannel(channel)
    }
    val stopIntent = Intent(context, SensorUpdateReceiver::class.java)
    stopIntent.action = if (isTransmitter) {
        SensorReceiverBase.ACTION_STOP_BEACON_TRANSMITTING
    } else {
        SensorReceiverBase.ACTION_STOP_BEACON_SCANNING
    }
    val stopPendingIntent = PendingIntent.getBroadcast(context, 0, stopIntent, PendingIntent.FLAG_MUTABLE)
    builder.addAction(0, context.getString(R.string.disable), stopPendingIntent)
    return builder
}
