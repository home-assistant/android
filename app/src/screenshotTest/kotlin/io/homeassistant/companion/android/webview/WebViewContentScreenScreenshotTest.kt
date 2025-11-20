package io.homeassistant.companion.android.webview

import androidx.compose.material3.SnackbarHostState
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
            snackbarHostState = SnackbarHostState(),
            playerSize = null,
            playerTop = 0.dp,
            playerLeft = 0.dp,
            currentAppLocked = false,
            customViewFromWebView = null,
        ) { }
    }

    @PreviewTest
    @Preview
    @Composable
    fun `WebView with app locked`() {
        WebViewContentScreen(
            webView = null,
            player = null,
            snackbarHostState = SnackbarHostState(),
            playerSize = null,
            playerTop = 0.dp,
            playerLeft = 0.dp,
            currentAppLocked = true,
            customViewFromWebView = null,
        ) { }
    }

    @PreviewTest
    @Preview
    @Composable
    fun `WebView with player`() {
        WebViewContentScreen(
            webView = null,
            player = FakePlayer(),
            snackbarHostState = SnackbarHostState(),
            playerSize = DpSize(100.dp, 100.dp),
            playerTop = 50.dp,
            playerLeft = 10.dp,
            currentAppLocked = false,
            customViewFromWebView = null,
        ) { }
    }
}
