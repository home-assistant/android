package io.homeassistant.companion.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.homeassistant.companion.android.util.cancelGroupIfNeeded

class NotificationDeleteReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_NOTIFICATION_GROUP = "EXTRA_NOTIFICATION_GROUP"
        const val EXTRA_NOTIFICATION_GROUP_ID = "EXTRA_NOTIFICATION_GROUP_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {

        val group = intent.getStringExtra(EXTRA_NOTIFICATION_GROUP)
        val groupId = intent.getIntExtra(EXTRA_NOTIFICATION_GROUP_ID, -1)

        val notificationManagerCompat = NotificationManagerCompat.from(context)

        // Cancel any left empty group of the notification, if needed
        // This maybe the case if timeoutAfter has deleted the notification
        // Then only the empty group is left and needs to be cancelled
        notificationManagerCompat.cancelGroupIfNeeded(group, groupId)
    }
}
