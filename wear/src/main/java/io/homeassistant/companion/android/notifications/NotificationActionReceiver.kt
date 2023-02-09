package io.homeassistant.companion.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.NotificationAction
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.common.util.cancel
import io.homeassistant.companion.android.database.notification.NotificationDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "NotifActionReceiver"
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var messagingManager: MessagingManager

    @Inject
    lateinit var notificationDao: NotificationDao

    override fun onReceive(context: Context, intent: Intent) {

        val notificationAction =
            intent.getParcelableExtra<NotificationAction>(NotificationData.EXTRA_NOTIFICATION_ACTION)

        if (notificationAction == null) {
            Log.e(TAG, "Failed to get notification action.")
            return
        }

        val tag = intent.getStringExtra(NotificationData.EXTRA_NOTIFICATION_TAG)
        val messageId = intent.getIntExtra(NotificationData.EXTRA_NOTIFICATION_ID, -1)
        val databaseId = intent.getLongExtra(NotificationData.EXTRA_NOTIFICATION_DB, 0)

        val isReply = notificationAction.key == "REPLY"
        var replyText: String? = null

        val onComplete: () -> Unit = {
            if (isReply && !replyText.isNullOrBlank()) {
                val replies = notificationAction.data.entries
                    .filter { it.key.startsWith(NotificationData.SOURCE_REPLY_HISTORY) }
                    .sortedBy { it.key.substringAfter(NotificationData.SOURCE_REPLY_HISTORY).toInt() }
                    .map { it.value } + replyText
                messagingManager.handleMessage(
                    replies
                        .takeLast(3)
                        .mapIndexed { index, text ->
                            "${NotificationData.SOURCE_REPLY_HISTORY}$index" to text!!
                        }
                        .toMap(),
                    "${NotificationData.SOURCE_REPLY}$databaseId"
                )
            } else {
                val notificationManagerCompat = NotificationManagerCompat.from(context)
                notificationManagerCompat.cancel(
                    tag,
                    messageId,
                    true
                )
            }
        }
        val onFailure: () -> Unit = {
            Handler(context.mainLooper).post {
                Toast.makeText(context, commonR.string.event_error, Toast.LENGTH_LONG).show()
            }
        }

        if (isReply) {
            replyText = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotificationData.KEY_TEXT_REPLY).toString()
            notificationAction.data += Pair("reply_text", replyText)
        }

        when (intent.action) {
            NotificationData.FIRE_EVENT -> {
                val serverId = notificationDao.get(databaseId.toInt())?.serverId ?: ServerManager.SERVER_ID_ACTIVE
                fireEvent(notificationAction, serverId, onComplete, onFailure)
            }
        }
    }

    private fun fireEvent(
        action: NotificationAction,
        serverId: Int,
        onComplete: () -> Unit,
        onFailure: () -> Unit
    ) {
        ioScope.launch {
            try {
                serverManager.integrationRepository(serverId).fireEvent(
                    "mobile_app_notification_action",
                    action.data
                        .filter { !it.key.startsWith(NotificationData.SOURCE_REPLY_HISTORY) }
                        .plus(Pair("action", action.key))
                )
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fire event.", e)
                onFailure()
            }
        }
    }
}
