package io.homeassistant.companion.android.webview

import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.media.player.HAMediaPlayer
import io.homeassistant.companion.android.util.compose.webview.HAWebView

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun WebViewContentScreen(
    webView: WebView?,
    player: Player?,
    playerSize: DpSize?,
    playerTop: Dp,
    playerLeft: Dp,
    currentAppLocked: Boolean,
    customViewFromWebView: View?,
    statusBarColor: Color? = null,
    navigationBarColor: Color? = null,
    onFullscreenClicked: (isFullscreen: Boolean) -> Unit,
) {
    HomeAssistantAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(commonR.color.colorLaunchScreenBackground)),
        ) {
            Column {
                statusBarColor?.Overlay(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
                HAWebView(
                    factory = {
                        webView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Transparent)
                        .then(if (currentAppLocked) Modifier.hazeEffect(style = HazeMaterials.thin()) else Modifier),
                )
                navigationBarColor?.Overlay(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }

            player?.let { player ->
                playerSize?.let { playerSize ->
                    HAMediaPlayer(
                        player = player,
                        contentScale = ContentScale.Inside,
                        modifier = Modifier
                            .offset(playerLeft, playerTop)
                            .size(playerSize),
                        fullscreenModifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        onFullscreenClicked = onFullscreenClicked,
                    )
                }
            }
            customViewFromWebView?.let { customViewFromWebView ->
                AndroidView(
                    factory = {
                        customViewFromWebView
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun Color.Overlay(modifier: Modifier) {
    Spacer(modifier = modifier.fillMaxWidth().background(this))
}

@Preview
@Composable
private fun WebViewContentScreenPreview() {
    WebViewContentScreen(
        webView = null,
        player = null,
        playerSize = null,
        playerTop = 0.dp,
        playerLeft = 0.dp,
        currentAppLocked = false,
        customViewFromWebView = null,
    ) { }
}
