package io.homeassistant.companion.android.util.compose.webview

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun HAWebView(
    // layoutParams: FrameLayout.LayoutParams,
    modifier: Modifier = Modifier,
    onCreated: WebView.() -> Unit = {},
    onRelease: (WebView) -> Unit = {},
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                onCreated(this)

                // this.layoutParams = layoutParams

                // TODO check how to restore state
                // this.restoreState(it)

                // webChromeClient = chromeClient
                // webViewClient = client
            }
        },
        modifier = modifier,
        onRelease = onRelease
    )
}
