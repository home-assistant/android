package io.homeassistant.companion.android.util

import android.app.Notification.FLAG_GROUP_SUMMARY
import android.app.NotificationManager
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

fun NotificationManagerCompat.getNotificationManager(): NotificationManager {
    val field = this.javaClass.declaredFields
        .toList().first { it.name == "mNotificationManager" }
    field.isAccessible = true
    val value = field.get(this)
    return value as NotificationManager
}

fun NotificationManagerCompat.getActiveNotification(tag: String?, id: Int): StatusBarNotification? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        this.getNotificationManager().activeNotifications.singleOrNull { s -> s.id == id && s.tag == tag }
    } else {
        return null
    }
}

fun NotificationManagerCompat.cancelGroupIfNeeded(tag: String?, id: Int): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        var currentActiveNotifications = this.getNotificationManager().activeNotifications

        // Get group key from the current notification
        // to handle possible group deletion
        var statusBarNotification =
            currentActiveNotifications.singleOrNull { s -> s.id == id && s.tag == tag && s.isGroup }
        var groupKey = statusBarNotification?.groupKey

        // Notification has a group?
        if (statusBarNotification != null && !groupKey.isNullOrBlank()) {
            // Yes it has a group. Get notifications of the group...
            val groupNotifications =
                currentActiveNotifications.filter { s -> s.groupKey == groupKey }

            // Is the notification which should be deleted a group summary
            var isGroupSummary = statusBarNotification.notification.flags and FLAG_GROUP_SUMMARY != 0

            // If the notification which should be delete is NOT a group summary AND
            // If there are only two left notifications, then this means only the current to be
            // canceled notification AND the group of the notification is left
            // If we cancel the group of the notification, the notifications inside of the group
            // will be canceled too.

            // If the notification which should be delete is A group summary AND
            // If there are only one left notifications, then this means only the empty group of
            // the notification is left.
            if (isGroupSummary && groupNotifications.size == 1 ||
                !isGroupSummary && groupNotifications.size == 2) {
                val group = groupNotifications[0].notification.group
                val groupId = group.hashCode()
                this.cancel(group, groupId)
                return true
            }
        }
    }
    return false
}

fun NotificationManagerCompat.cancel(tag: String?, id: Int, cancelGroup: Boolean) {
    if (cancelGroup && cancelGroupIfNeeded(tag, id)) return

    // Clear notification
    this.cancel(tag, id)
}
