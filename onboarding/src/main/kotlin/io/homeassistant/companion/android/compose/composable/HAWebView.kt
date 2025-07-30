package io.homeassistant.companion.android.compose.composable

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.homeassistant.companion.android.common.data.HomeAssistantApis
import timber.log.Timber

// TODO remove this in favor of the one in the :app

@Composable
fun HAWebView(modifier: Modifier = Modifier, configure: WebView.() -> Unit = {}, factory: () -> WebView? = { null }) {
    AndroidView(
        factory = { context ->
            (factory() ?: WebView(context)).apply {
                // We want the modifier to determine the size so the WebView should match the parent
                this.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                defaultSettings()
                configure(this)
            }
        },
        modifier = modifier,
    )
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
