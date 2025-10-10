package io.homeassistant.companion.android.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.util.compose.media.player.FakePlayer

class WebViewContentScreenScreenshotTest {

    @PreviewTest
    @Preview
    @Composable
    fun `WebView with app unlocked`() {
        WebViewContentScreen(
            webView = null,
            player = null,
            playerSize = null,
            playerTop = 0.dp,
            playerLeft = 0.dp,
            currentAppLocked = false,
            customViewFromWebView = null,
            onFullscreenClicked = { },
            serverHandleInsets = false,
        )
    }

    @PreviewTest
    @Preview
    @Composable
    fun `WebView with app unlocked server handle insets`() {
        WebViewContentScreen(
            webView = null,
            player = null,
            playerSize = null,
            playerTop = 0.dp,
            playerLeft = 0.dp,
            currentAppLocked = false,
            customViewFromWebView = null,
            onFullscreenClicked = { },
            serverHandleInsets = true,
        )
    }

    @PreviewTest
    @Preview
    @Composable
    fun `WebView with app locked`() {
        WebViewContentScreen(
            webView = null,
            player = null,
            playerSize = null,
            playerTop = 0.dp,
            playerLeft = 0.dp,
            currentAppLocked = true,
            customViewFromWebView = null,
            onFullscreenClicked = { },
            serverHandleInsets = false,
        )
    }

    @PreviewTest
    @Preview
    @Composable
    fun `WebView with player`() {
        WebViewContentScreen(
            webView = null,
            player = FakePlayer(),
            playerSize = DpSize(100.dp, 100.dp),
            playerTop = 50.dp,
            playerLeft = 10.dp,
            currentAppLocked = false,
            customViewFromWebView = null,
            onFullscreenClicked = { },
            serverHandleInsets = false,
        )
    }
}
