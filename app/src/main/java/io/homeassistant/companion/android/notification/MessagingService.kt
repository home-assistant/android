package io.homeassistant.companion.android.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import io.homeassistant.companion.android.util.extensions.isAbsoluteUrl
import io.homeassistant.companion.android.webview.WebViewActivity

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MessagingService : AbstractMessagingService() {

    override fun handleIntent(notificationTag: String?, messageId: Int, actionUrl: String?): PendingIntent {
        val intent = if (actionUrl.isAbsoluteUrl()) {
            Intent(Intent.ACTION_VIEW).setData(Uri.parse(actionUrl))
        } else {
            WebViewActivity.newInstance(this, actionUrl)
        }
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return PendingIntent.getActivity(this, 0, intent, 0)
    }

    override fun actionHandler(): Class<*> = NotificationActionReceiver::class.java
}