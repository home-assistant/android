package io.homeassistant.companion.android.util.extensions

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

val Context.notificationManager: NotificationManager
    get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

@TargetApi(Build.VERSION_CODES.O)
fun Context.saveChannel(
    channelId: String,
    channelName: String,
    lightColor: Int? = null,
    importance: Int = NotificationManager.IMPORTANCE_HIGH,
    notifyUser: Boolean = true
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = notificationManager
        val channel = manager.getNotificationChannel(channelId)
        if (channel == null) {
            val newChannel = NotificationChannel(channelId, channelName, importance)
            newChannel.enableLights(true)
            if (lightColor != null) {
                newChannel.lightColor = getColor(lightColor)
            }
            newChannel.enableVibration(notifyUser)
            newChannel.lockscreenVisibility = if (notifyUser) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_SECRET
            newChannel.setShowBadge(notifyUser)
            manager.createNotificationChannel(newChannel)
        }
    }
}