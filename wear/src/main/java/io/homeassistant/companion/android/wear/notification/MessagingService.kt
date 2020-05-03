package io.homeassistant.companion.android.wear.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import io.homeassistant.companion.android.notification.AbstractMessagingService

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MessagingService : AbstractMessagingService() {

    companion object {
        const val EXTRA_ACTION_URL = "MessagingService.ACTION_URL"
    }

    override fun handleIntent(actionUrl: String?): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java)
            .putExtra(EXTRA_ACTION_URL, actionUrl)

        return PendingIntent.getBroadcast(this, 0, intent, 0)
    }

}