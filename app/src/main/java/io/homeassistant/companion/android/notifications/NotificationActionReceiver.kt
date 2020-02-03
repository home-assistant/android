package io.homeassistant.companion.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        const val EXTRA_NOTIFICATION_ACTION = "EXTRA_ACTION_KEY"
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onReceive(context: Context?, intent: Intent?) {
        DaggerServiceComponent.builder()
            .appComponent((context!!.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        intent?.getParcelableExtra<NotificationAction>(EXTRA_NOTIFICATION_ACTION)?.let {
            ioScope.launch {
                integrationUseCase.fireEvent(
                    "mobile_app_notification_action",
                    it.data.plus(Pair("action", it.key))
                )
            }
        }
    }
}
