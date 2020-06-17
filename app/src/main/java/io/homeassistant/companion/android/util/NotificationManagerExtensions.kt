package io.homeassistant.companion.android.util

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.os.Build

@SuppressLint("NewApi")
fun NotificationManager.cancel(tag: String?, id: Int, cancelGroup: Boolean) {

    var groupKey: String? = null
    var doCancelGroup = cancelGroup && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    if (doCancelGroup) {
        // Get group key from current notification
        // to handle possible group deletion
        groupKey =
            this.activeNotifications.singleOrNull { s -> s.id == id && s.tag == tag && s.isGroup }?.groupKey
    }

    // Clear notification
    this.cancel(tag, id)

    if (doCancelGroup && !groupKey.isNullOrBlank()) {
        // Get notifications of the group
        val groupNotifications =
            this.activeNotifications.filter { s -> s.groupKey == groupKey }

        // Only one left. That means. Just the group itself is left.
        // Then clear the group
        if (groupNotifications.size == 1) {
            val group = groupNotifications[0].notification.group
            val groupId = group.hashCode()
            this.cancel(group, groupId)
        }
    }
}
