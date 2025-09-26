package io.homeassistant.companion.android.compose.composable

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import timber.log.Timber

// TODO remove this in favor of the one in the :app (or move WebView in common but it means having webview library on the wear module)

@VisibleForTesting const val HA_WEBVIEW_TAG = "ha_web_view_tag"

@Composable
fun HAWebView(
    modifier: Modifier = Modifier,
    configure: WebView.() -> Unit = {},
    factory: () -> WebView? = { null },
    // Only used when the backstack of the webView is empty
    onBackPressed: (() -> Unit)? = null,
) {
    var webview by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = { context ->
            (factory() ?: WebView(context)).apply {
                webview = this
                // We want the modifier to determine the size so the WebView should match the parent
                this.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                defaultSettings()
                configure(this)
            }
        },
        modifier = modifier.testTag(HA_WEBVIEW_TAG),
        onRelease = {
            Timber.d("onRelease WebView, stopping loading")
            it.stopLoading()
            webview = null
        },
    )

    // TODO do this in :app
    // To avoid checking doUpdateVisitedHistory from the webViewClient we simply delegate the back button handling
    // to the webView and when the webview backstack is empty we call the callback given in parameter that should be
    // handle by the navHost.
    BackHandler(onBackPressed != null) {
        webview.takeIf { it?.canGoBack() == true }?.goBack() ?: onBackPressed?.invoke()
    }
}

fun WebView.settings(configureDsl: WebSettings.() -> Unit) {
    runCatching { settings.configureDsl() }.onFailure {
        Timber.e(it, "Failed to configure WebView settings")
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.defaultSettings() {
    settings {
        // https://github.com/home-assistant/android/pull/3353
        minimumFontSize = 5
        javaScriptEnabled = true
        domStorageEnabled = true
        // https://github.com/home-assistant/android/pull/2252
        displayZoomControls = false
        userAgentString += " ${HomeAssistantApis.USER_AGENT_STRING}"
    }
    setBackgroundColor(Color.TRANSPARENT)
}
