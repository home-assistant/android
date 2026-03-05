package io.homeassistant.companion.android.common.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.homeassistant.companion.android.common.util.cancelGroupIfNeeded

class NotificationDeleteReceiver : BroadcastReceiver() {
    companion object {
        private const val EXTRA_DATA_KEYS = "EXTRA_DATA_KEYS"
        private const val EXTRA_DATA_VALUES = "EXTRA_DATA_VALUES"
        private const val EXTRA_NOTIFICATION_GROUP = "EXTRA_NOTIFICATION_GROUP"
        private const val EXTRA_NOTIFICATION_GROUP_ID = "EXTRA_NOTIFICATION_GROUP_ID"
        private const val EXTRA_NOTIFICATION_DB = "EXTRA_NOTIFICATION_DB"

        /**
         * Creates a [PendingIntent] that fires a notification cleared event when triggered.
         *
         * @param context The context to use for creating the intent.
         * @param data The event data to send to the Home Assistant server.
         * @param messageId The unique ID for the PendingIntent request code.
         * @param group The notification group name, if any.
         * @param groupId The notification group ID.
         * @param databaseId The database ID of the notification.
         */
        fun createDeletePendingIntent(
            context: Context,
            data: Map<String, String>,
            messageId: Int,
            group: String?,
            groupId: Int,
            databaseId: Long?,
        ): PendingIntent {
            val deleteIntent = Intent(context, NotificationDeleteReceiver::class.java).apply {
                putExtra(EXTRA_DATA_KEYS, data.keys.toTypedArray())
                putExtra(EXTRA_DATA_VALUES, data.values.toTypedArray())
                putExtra(EXTRA_NOTIFICATION_GROUP, group)
                putExtra(EXTRA_NOTIFICATION_GROUP_ID, groupId)
                putExtra(EXTRA_NOTIFICATION_DB, databaseId)
            }
            return PendingIntent.getBroadcast(
                context,
                messageId,
                deleteIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventDataKeys = intent.getStringArrayExtra(EXTRA_DATA_KEYS) ?: emptyArray()
        val eventDataValues = intent.getStringArrayExtra(EXTRA_DATA_VALUES) ?: emptyArray()
        val group = intent.getStringExtra(EXTRA_NOTIFICATION_GROUP)
        val groupId = intent.getIntExtra(EXTRA_NOTIFICATION_GROUP_ID, -1)
        val databaseId = intent.getLongExtra(EXTRA_NOTIFICATION_DB, 0)

        val notificationManagerCompat = NotificationManagerCompat.from(context)

        // Cancel any left empty group of the notification, if needed
        // This maybe the case if timeoutAfter has deleted the notification
        // Then only the empty group is left and needs to be cancelled
        notificationManagerCompat.cancelGroupIfNeeded(group, groupId)

        NotificationDeleteWorker.enqueue(context, databaseId, eventDataKeys, eventDataValues)
    }
}
