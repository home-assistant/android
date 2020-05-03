package io.homeassistant.companion.android.notification

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.homeassistant.companion.android.util.extensions.catch
import io.homeassistant.companion.android.util.extensions.isAbsoluteUrl
import io.homeassistant.companion.android.webview.WebViewActivity

class NotificationActionReceiver : AbstractNotificationActionReceiver() {

    override fun openUri(
        context: Context,
        action: NotificationAction,
        onComplete: () -> Unit,
        onFailure: (Int) -> Unit
    ) {
        val intent = if (action.uri.isAbsoluteUrl()) {
            Intent(Intent.ACTION_VIEW).setData(Uri.parse(action.uri))
        } else {
            WebViewActivity.newInstance(context, action.uri)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        onComplete()
    }

}