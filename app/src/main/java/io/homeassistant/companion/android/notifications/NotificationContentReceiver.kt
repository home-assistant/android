package io.homeassistant.companion.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import io.homeassistant.companion.android.util.UrlHandler
import io.homeassistant.companion.android.util.cancelGroupIfNeeded
import io.homeassistant.companion.android.webview.WebViewActivity

class NotificationContentReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_NOTIFICATION_GROUP = "EXTRA_NOTIFICATION_GROUP"
        const val EXTRA_NOTIFICATION_GROUP_ID = "EXTRA_NOTIFICATION_GROUP_ID"
        const val EXTRA_NOTIFICATION_ACTION = "EXTRA_NOTIFICATION_ACTION"
    }

    override fun onReceive(context: Context, intent: Intent) {

        val action = intent.getStringExtra(EXTRA_NOTIFICATION_ACTION)
        val group = intent.getStringExtra(EXTRA_NOTIFICATION_GROUP)
        val groupId = intent.getIntExtra(EXTRA_NOTIFICATION_GROUP_ID, -1)

        val notificationManagerCompat = NotificationManagerCompat.from(context)

        // Cancel any left empty group of the notification, if needed
        // This is the case if the user clicked on the notification
        // Then only the empty group is left and needs to be cancelled
        notificationManagerCompat.cancelGroupIfNeeded(group, groupId)

        if (!action.isNullOrBlank()) {
            val intent = if (UrlHandler.isAbsoluteUrl(action)) {
                Intent(Intent.ACTION_VIEW).apply {
                    this.data = Uri.parse(action)
                }
            } else {
                WebViewActivity.newInstance(context, action)
            }

            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            context.startActivity(intent)
        }
    }
}
