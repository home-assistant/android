package io.homeassistant.companion.android.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val FIRE_EVENT = "FIRE_EVENT"
        const val OPEN_URI = "OPEN_URI"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_ACTION = "EXTRA_ACTION_KEY"
        const val EXTRA_URI = "EXTRA_URI"
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onReceive(context: Context, intent: Intent) {
        DaggerServiceComponent.builder()
            .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        when (intent.action) {
            FIRE_EVENT -> fireEvent(intent)
            OPEN_URI -> openUri(context, intent)
        }

        val messageId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (messageId != -1) {
            val notificationService: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationService.cancel(messageId)
        }
    }

    private fun fireEvent(intent: Intent) {
        intent.getParcelableExtra<NotificationAction>(EXTRA_NOTIFICATION_ACTION)?.let {
            ioScope.launch {
                integrationUseCase.fireEvent(
                    "mobile_app_notification_action",
                    it.data.plus(Pair("action", it.key))
                )
            }
        }
    }

    private fun openUri(context: Context, intent: Intent) {
        val newIntent = Intent(Intent.ACTION_VIEW)
        newIntent.data = Uri.parse(intent.getStringExtra(EXTRA_URI))
        newIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(newIntent)
    }
}
