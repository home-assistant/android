package io.homeassistant.companion.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.homeassistant.companion.android.util.NotificationActionContentHandler
import io.homeassistant.companion.android.util.cancelGroupIfNeeded

class NotificationContentReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_NOTIFICATION_GROUP = "EXTRA_NOTIFICATION_GROUP"
        const val EXTRA_NOTIFICATION_GROUP_ID = "EXTRA_NOTIFICATION_GROUP_ID"
        const val EXTRA_NOTIFICATION_ACTION_URI = "EXTRA_NOTIFICATION_ACTION_URI"
    }

    override fun onReceive(context: Context, intent: Intent) {

        val actionUri = intent.getStringExtra(EXTRA_NOTIFICATION_ACTION_URI)
        val group = intent.getStringExtra(EXTRA_NOTIFICATION_GROUP)
        val groupId = intent.getIntExtra(EXTRA_NOTIFICATION_GROUP_ID, -1)

        val notificationManagerCompat = NotificationManagerCompat.from(context)

        // Cancel any left empty group of the notification, if needed
        // This is the case if the user clicked on the notification
        // Then only the empty group is left and needs to be cancelled
        notificationManagerCompat.cancelGroupIfNeeded(group, groupId)

        NotificationActionContentHandler.openUri(context, actionUri)
    }
}
