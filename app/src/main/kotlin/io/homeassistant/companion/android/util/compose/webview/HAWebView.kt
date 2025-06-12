package io.homeassistant.companion.android.util.compose.webview

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun HAWebView(
    // layoutParams: FrameLayout.LayoutParams,
    modifier: Modifier = Modifier,
    configure: WebView.() -> Unit = {},
    onRelease: (WebView) -> Unit = {},
    factory: () -> WebView? = { null },
) {
    AndroidView(
        factory = { context ->
            (factory() ?: WebView(context)).apply {
                configure(this)

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
