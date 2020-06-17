package io.homeassistant.companion.android.util

import android.app.NotificationManager
import android.os.Build

fun NotificationManager.cancel(tag: String?, id: Int, cancelGroup: Boolean) {

    if (cancelGroup && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        var currentActiveNotifications = this.activeNotifications

        // Get group key from current notification
        // to handle possible group deletion
        var groupKey =
            currentActiveNotifications.singleOrNull { s -> s.id == id && s.tag == tag && s.isGroup }?.groupKey

        // Notification has a group?
        if (!groupKey.isNullOrBlank()) {
            // Yes it has a group get notifications of the group...
            val groupNotifications =
                currentActiveNotifications.filter { s -> s.groupKey == groupKey }

            // If the are only two left notifications, then this means only the current to be
            // canceled notification AND the group of the notification is left
            // If we cancel the group of the notification, the notifications inside of the group
            // will be canceled too.
            if (groupNotifications.size == 2) {
                val group = groupNotifications[0].notification.group
                val groupId = group.hashCode()
                this.cancel(group, groupId)
                return
            }
        }
    }

    // Clear notification
    this.cancel(tag, id)
}
