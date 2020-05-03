package io.homeassistant.companion.android.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.widget.Toast
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.resources.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class AbstractNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "NotifActionReceiver"
        const val FIRE_EVENT = "FIRE_EVENT"
        const val OPEN_URI = "OPEN_URI"
        const val EXTRA_NOTIFICATION_TAG = "EXTRA_NOTIFICATION_TAG"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_ACTION = "EXTRA_ACTION_KEY"
    }

    protected val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val graphAccessor = context.applicationContext as GraphComponentAccessor
        DaggerNotificationComponent.factory().create(graphAccessor.appComponent, graphAccessor.domainComponent)
            .inject(this)

        val notificationAction =
            intent.getParcelableExtra<NotificationAction>(EXTRA_NOTIFICATION_ACTION)

        if (notificationAction == null) {
            Log.e(TAG, "Failed to get notification action.")
            return
        }

        val tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        val messageId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val onComplete: () -> Unit = {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(tag, messageId)
        }
        val onFailure: (Int) -> Unit = { resourceId ->
            Handler(context.mainLooper).post {
                Toast.makeText(context, resourceId, Toast.LENGTH_LONG).show()
            }
        }
        when (intent.action) {
            FIRE_EVENT -> fireEvent(notificationAction, onComplete, onFailure)
            OPEN_URI -> openUri(context, notificationAction, onComplete, onFailure)
        }

        // Make sure the notification shade closes
        context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
    }

    private fun fireEvent(
        action: NotificationAction,
        onComplete: () -> Unit,
        onFailure: (Int) -> Unit
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
                onFailure(R.string.event_error)
            }
        }
    }

    protected abstract fun openUri(
        context: Context,
        action: NotificationAction,
        onComplete: () -> Unit,
        onFailure: (Int) -> Unit
    )

}
