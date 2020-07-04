package io.homeassistant.companion.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.homeassistant.companion.android.webview.WebViewActivity

object NotificationActionContentHandler {
    fun openUri(context: Context, uri: String?, onComplete: () -> Unit = {}) {
        if (!uri.isNullOrBlank()) {
            val intent = if (UrlHandler.isAbsoluteUrl(uri)) {
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(uri)
                }
            } else {
                WebViewActivity.newInstance(context, uri)
            }

            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            onComplete()
        }
    }
}
