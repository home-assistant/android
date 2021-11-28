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
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.util.NotificationActionContentHandler
import io.homeassistant.companion.android.util.cancel
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
        const val FIRE_EVENT = "FIRE_EVENT"
        const val OPEN_URI = "OPEN_URI"
        const val EXTRA_NOTIFICATION_TAG = "EXTRA_NOTIFICATION_TAG"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_ACTION = "EXTRA_ACTION_KEY"
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onReceive(context: Context, intent: Intent) {

        val notificationAction =
            intent.getParcelableExtra<NotificationAction>(EXTRA_NOTIFICATION_ACTION)

        if (notificationAction == null) {
            Log.e(TAG, "Failed to get notification action.")
            return
        }

        val tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        val messageId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val onComplete: () -> Unit = {
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            notificationManagerCompat.cancel(
                tag,
                messageId,
                true
            )
        }
        val onFailure: () -> Unit = {
            Handler(context.mainLooper).post {
                Toast.makeText(context, commonR.string.event_error, Toast.LENGTH_LONG).show()
            }
        }
        if (notificationAction.key == "REPLY") {
            notificationAction.data += Pair(
                "reply_text",
                RemoteInput.getResultsFromIntent(intent)
                    .getCharSequence(MessagingService.KEY_TEXT_REPLY).toString()
            )
        }

        when (intent.action) {
            FIRE_EVENT -> fireEvent(notificationAction, onComplete, onFailure)
            OPEN_URI -> NotificationActionContentHandler.openUri(
                context,
                notificationAction.uri,
                onComplete
            )
        }
    }

    private fun fireEvent(
        action: NotificationAction,
        onComplete: () -> Unit,
        onFailure: () -> Unit
    ) {
        ioScope.launch {
            try {
                integrationUseCase.fireEvent(
                    "mobile_app_notification_action",
                    action.data.plus(Pair("action", action.key))
                )
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fire event.", e)
                onFailure()
            }
        }
    }
}
