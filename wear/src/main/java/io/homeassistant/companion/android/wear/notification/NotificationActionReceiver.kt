package io.homeassistant.companion.android.wear.notification

import android.content.Context
import io.homeassistant.companion.android.notification.AbstractNotificationActionReceiver
import io.homeassistant.companion.android.notification.NotificationAction
import io.homeassistant.companion.android.wear.R
import kotlinx.coroutines.launch
import javax.inject.Inject

class NotificationActionReceiver : AbstractNotificationActionReceiver() {

    @Inject lateinit var uriLauncher: NotificationActionUriLauncher

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