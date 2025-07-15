package io.homeassistant.companion.android.util.compose.webview

import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A composable that displays a WebView.
 *
 * This function provides a convenient way to embed a WebView in a Jetpack Compose UI.
 * It allows for customization of the WebView through the [configure] lambda.
 *
 * The WebView will be sized to match the [modifier].
 *
 * @param factory A lambda that creates the WebView instance. If this returns null, a new
 *                WebView will be created with the current context. This is useful for providing
 *                a pre-configured WebView instance.
 */
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

                configure(this)
            }
        },
        modifier = modifier,
    )
}
