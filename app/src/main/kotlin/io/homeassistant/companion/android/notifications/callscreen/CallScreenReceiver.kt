package io.homeassistant.companion.android.notifications.callscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.notifications.callscreen.CallScreenManager.Companion.ACTION_DECLINE
import io.homeassistant.companion.android.notifications.callscreen.CallScreenManager.Companion.ACTION_HANG_UP
import io.homeassistant.companion.android.notifications.callscreen.CallScreenManager.Companion.ACTION_RUN
import io.homeassistant.companion.android.notifications.callscreen.CallScreenManager.Companion.EXTRA_ACTION_INDEX
import io.homeassistant.companion.android.notifications.callscreen.CallScreenManager.Companion.EXTRA_CALL_ID
import io.homeassistant.companion.android.notifications.callscreen.CallScreenManager.Companion.EXTRA_TAG
import javax.inject.Inject
import timber.log.Timber

/** Handles the notification buttons of a call: decline, custom actions and hang up. */
@AndroidEntryPoint
internal class CallScreenReceiver : BroadcastReceiver() {

    @Inject
    lateinit var callScreenManager: CallScreenManager

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getLongExtra(EXTRA_CALL_ID, -1)
        val activeCallId = callScreenManager.activeCall.value?.config?.callId
        if (callId != activeCallId) {
            // Stale notification, e.g. the process was killed while the call was ringing
            Timber.d("Ignoring call screen action for an inactive call")
            intent.getStringExtra(EXTRA_TAG)?.let {
                NotificationManagerCompat.from(context).cancel(it, it.hashCode())
            }
            return
        }
        when (intent.action) {
            ACTION_DECLINE -> callScreenManager.decline()
            ACTION_RUN -> callScreenManager.runAction(intent.getIntExtra(EXTRA_ACTION_INDEX, 0))
            ACTION_HANG_UP -> callScreenManager.hangUp()
        }
    }
}
