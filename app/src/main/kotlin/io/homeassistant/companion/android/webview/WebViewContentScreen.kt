package io.homeassistant.companion.android.webview

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.media.player.HAMediaPlayer
import io.homeassistant.companion.android.util.compose.webview.HAWebView

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun WebViewContentScreen(
    webView: WebView?,
    player: Player?,
    snackbarHostState: SnackbarHostState,
    playerSize: DpSize?,
    playerTop: Dp,
    playerLeft: Dp,
    currentAppLocked: Boolean,
    customViewFromWebView: View?,
    nightModeTheme: NightModeTheme? = null,
    statusBarColor: Color? = null,
    backgroundColor: Color? = null,
    onFullscreenClicked: (isFullscreen: Boolean) -> Unit,
) {
    HomeAssistantAppTheme {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    snackbarHostState,
                    modifier =
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            },
            // Delegate the insets handling to the webview
            contentWindowInsets = WindowInsets(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorResource(commonR.color.colorLaunchScreenBackground)),
            ) {
                SafeHAWebView(webView, nightModeTheme, currentAppLocked, statusBarColor, backgroundColor)

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
}

@Composable
private fun SafeHAWebView(
    webView: WebView?,
    nightModeTheme: NightModeTheme?,
    currentAppLocked: Boolean,
    statusBarColor: Color?,
    backgroundColor: Color?,
) {
    // We add colored small spacer all around the WebView based on the `safeDrawing` insets.
    // TODO This should be disable when the frontend supports edge to edge
    // https://github.com/home-assistant/frontend/pull/25566

    val insets = WindowInsets.safeDrawing
    val insetsPaddingValues = insets.asPaddingValues()

    Column(modifier = if (currentAppLocked) Modifier.hazeEffect(style = HazeMaterials.thin()) else Modifier) {
        statusBarColor?.Overlay(
            modifier = Modifier
                .height(insetsPaddingValues.calculateTopPadding())
                .fillMaxWidth()
                // We don't want the status bar to color the left and right areas
                .padding(insets.only(WindowInsetsSides.Horizontal).asPaddingValues()),
        )
        // The height is based on whatever is left between the statusBar and navigationBar
        Row(modifier = Modifier.weight(1f)) {
            // Left safe area
            backgroundColor?.Overlay(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(insetsPaddingValues.calculateLeftPadding(LayoutDirection.Ltr)),
            )
            HAWebView(
                nightModeTheme = nightModeTheme,
                factory = {
                    webView
                },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Transparent),
            )
            // Right safe area
            backgroundColor?.Overlay(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(insetsPaddingValues.calculateRightPadding(LayoutDirection.Ltr)),
            )
        }
        backgroundColor?.Overlay(
            modifier = Modifier
                .fillMaxWidth()
                .height(insetsPaddingValues.calculateBottomPadding()),
        )
    }
}

@Composable
private fun Color.Overlay(modifier: Modifier) {
    Spacer(
        modifier = modifier
            .background(this),
    )
}

@Preview
@Composable
private fun WebViewContentScreenPreview() {
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
