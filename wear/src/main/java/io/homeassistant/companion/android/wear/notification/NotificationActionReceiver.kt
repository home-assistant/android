package io.homeassistant.companion.android.wear.notification

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.notification.AbstractNotificationActionReceiver
import io.homeassistant.companion.android.notification.DaggerNotificationComponent
import io.homeassistant.companion.android.notification.NotificationAction
import io.homeassistant.companion.android.wear.R
import io.homeassistant.companion.android.wear.background.BackgroundModule
import kotlinx.coroutines.launch
import javax.inject.Inject

class NotificationActionReceiver : AbstractNotificationActionReceiver() {

    @Inject lateinit var uriLauncher: NotificationActionUriLauncher

    override fun onReceive(context: Context, intent: Intent) {
        val graphAccessor = context.applicationContext as GraphComponentAccessor
        DaggerNotificationAppComponent.factory().create(graphAccessor.appComponent).inject(this)
        super.onReceive(context, intent)
    }

    override fun openUri(
        context: Context,
        action: NotificationAction,
        onComplete: () -> Unit,
        onFailure: (Int) -> Unit
    ) {
        val actionUri = action.uri ?: return
        ioScope.launch {
            if (uriLauncher.launchAction(actionUri)) {
                onComplete()
            } else {
                onFailure(R.string.failed_launch_action_uri)
            }
        }
    }

}