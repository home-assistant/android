package io.homeassistant.companion.android.util

import android.app.Notification.FLAG_GROUP_SUMMARY
import android.app.NotificationManager
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat

const val TAG = "NotifManagerCompat"

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
        Log.d(TAG, "Cancel notification with tag \"$tag\" and id \"$id\"")

        var currentActiveNotifications = this.getNotificationManager().activeNotifications

        Log.d(TAG, "Check if the notification is in a group...")
        // Get group key from the current notification
        // to handle possible group deletion
        var statusBarNotification =
            currentActiveNotifications.singleOrNull { s -> s.id == id && s.tag == tag && s.isGroup }
        var groupKey = statusBarNotification?.groupKey

        // Notification has a group?
        if (statusBarNotification != null && !groupKey.isNullOrBlank()) {
            // Yes it has a group.
            Log.d(TAG, "Notification is in a group ($groupKey). Get all notifications for this group...")

            // Check if the group is the auto group of android ("ranker_group")
            if (!groupKey.endsWith("|g:ranker_group")) {

                // Nope it is a custom group. Get notifications of the group...
                val groupNotifications =
                    currentActiveNotifications.filter { s -> s.groupKey == groupKey }

                // Is the notification which should be deleted a group summary
                var isGroupSummary = statusBarNotification.notification.flags and FLAG_GROUP_SUMMARY != 0
                if (isGroupSummary) Log.d(TAG, "Notification is the group summary.")
                else Log.d(TAG, "Notification is NOT the group summary.")

                // If the notification which should be delete is NOT a group summary AND
                // If there are only two left notifications, then this means only the current to be
                // canceled notification AND the group of the notification is left
                // If we cancel the group of the notification, the notifications inside of the group
                // will be canceled too.

                // If the notification which should be delete is A group summary AND
                // If there are only one left notifications, then this means only the empty group of
                // the notification is left.
                if (isGroupSummary && groupNotifications.size == 1 ||
                    !isGroupSummary && groupNotifications.size == 2
                ) {
                    val group = groupNotifications[0].notification.group

                    if (isGroupSummary) Log.d(TAG, "Notification is the group summary \"$group\" with no notifications inside. Try to cancel this group summary notification...")
                    else Log.d(TAG, "Notification is inside of group \"$group\", but is the last one in the group. Try to cancel the group notification....")
                    // If group is null, the group is a group which is generate by the system.
                    // This group can't be canceled, but it will be canceled by canceling the last notification inside of the group
                    // If the group isn't null, cancel the group
                    return if (group != null) {
                        var groupId = group.hashCode()
                        Log.d(TAG, "Cancel group notification with tag \"$group\"  and id \"$groupId\"")
                        this.cancel(group, groupId)
                        true
                    } else {
                        Log.d(TAG, "Cannot cancel group notification, because group tag is empty. Anyway cancel notification.")
                        false
                    }
                } else {
                    if (isGroupSummary && groupNotifications.size != 1) Log.d(
                        TAG,
                        "Notification is the group summary, but the group has more than or no notifications inside (" + groupNotifications.size + "). Cancel notification"
                    )
                    else if (!isGroupSummary && groupNotifications.size != 2) Log.d(
                        TAG,
                        "Notification is in a group, but the group has more/less than 2 notifications inside (" + groupNotifications.size + "). Cancel notification"
                    )
                }
            } else {
                Log.d(TAG, "Notification is in a group ($groupKey), but it is in the auto group. Cancel notification")
            }
        } else {
            if (statusBarNotification == null) Log.d(TAG, "Notification is not in a group. Cancel notification...")
            else if (groupKey.isNullOrBlank()) Log.d(TAG, "Notification is in a group but has no group key. Cancel notification")
        }
    }
    return false
}

fun NotificationManagerCompat.cancel(tag: String?, id: Int, cancelGroup: Boolean) {
    if (cancelGroup && cancelGroupIfNeeded(tag, id)) return

    // Clear notification
    this.cancel(tag, id)
}
