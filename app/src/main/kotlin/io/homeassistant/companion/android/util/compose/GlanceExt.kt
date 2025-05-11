package io.homeassistant.companion.android.util.compose

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import io.homeassistant.companion.android.webview.WebViewActivity

@Composable
@ReadOnlyComposable
fun glanceStringResource(@StringRes id: Int, vararg arguments: Any): String =
    LocalContext.current.getString(id, *arguments)

/**
 * Get an Action that will open the [WebViewActivity] for the given [path]
 */
@Composable
fun actionStartWebView(path: String, serverId: Int): Action {
    val intent = Intent(
        WebViewActivity.newInstance(LocalContext.current, path, serverId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    intent.action = path
    intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    return actionStartActivity(intent)
}
