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
    importance: String? = null,
    notifyUser: Boolean = true,
    vibrationPattern: String? = null
) {
    val channelImportance = when (importance) {
        "high" -> NotificationManager.IMPORTANCE_HIGH
        "low" -> NotificationManager.IMPORTANCE_LOW
        "max" -> NotificationManager.IMPORTANCE_MAX
        "min" -> NotificationManager.IMPORTANCE_MIN
        else -> NotificationManager.IMPORTANCE_DEFAULT
    }
    val notificationVibrationPattern = vibrationPattern.parseVibrationPattern()
    saveChannel(
        channelId,
        channelName,
        lightColor,
        channelImportance,
        notifyUser,
        notificationVibrationPattern
    )
}

@TargetApi(Build.VERSION_CODES.O)
fun Context.saveChannel(
    channelId: String,
    channelName: String,
    lightColor: Int? = null,
    importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    notifyUser: Boolean = true,
    vibrationPattern: LongArray = longArrayOf()
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = notificationManager
        val channel = manager.getNotificationChannel(channelId)
        if (channel == null) {
            val newChannel = NotificationChannel(channelId, channelName, importance)
            if (lightColor != null) {
                newChannel.enableLights(true)
                newChannel.lightColor = getColor(lightColor)
            }
            newChannel.enableVibration(notifyUser)
            newChannel.lockscreenVisibility = if (notifyUser) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_SECRET
            newChannel.setShowBadge(notifyUser)
            if (vibrationPattern.isNotEmpty()) {
                newChannel.vibrationPattern = vibrationPattern
            }
            manager.createNotificationChannel(newChannel)
        }
    }
}

fun String?.parseVibrationPattern(): LongArray {
    if (this == null || isBlank()) {
        return longArrayOf()
    }
    val pattern = split(",").toTypedArray()
    val patterns = pattern.mapNotNull { entry -> entry.trim().toLongOrNull() }
    return if (patterns.isEmpty()) {
        patterns.toLongArray()
    } else {
        longArrayOf()
    }
}
