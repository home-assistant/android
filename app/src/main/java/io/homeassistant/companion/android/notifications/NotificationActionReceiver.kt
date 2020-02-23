package io.homeassistant.companion.android.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.util.UrlHandler
import io.homeassistant.companion.android.webview.WebViewActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onReceive(context: Context, intent: Intent) {
        DaggerServiceComponent.builder()
            .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
        val notificationAction =
            intent.getParcelableExtra<NotificationAction>(EXTRA_NOTIFICATION_ACTION)

        if (notificationAction == null) {
            Log.e(TAG, "Failed to get notification action.")
            return
        }

        when (intent.action) {
            FIRE_EVENT -> fireEvent(notificationAction)
            OPEN_URI -> openUri(context, notificationAction)
        }

        val tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        val messageId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (messageId != -1) {
            val notificationService: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationService.cancel(tag, messageId)
        }

        // Make sure the notification shade closes
        context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }

    private fun fireEvent(action: NotificationAction) {
        ioScope.launch {
            integrationUseCase.fireEvent(
                "mobile_app_notification_action",
                action.data.plus(Pair("action", action.key))
            )
        }
    }

    private fun openUri(context: Context, action: NotificationAction) {
        val intent = if (UrlHandler.isAbsoluteUrl(action.uri)) {
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(action.uri)
            }
        } else {
            WebViewActivity.newInstance(context, action.uri)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
