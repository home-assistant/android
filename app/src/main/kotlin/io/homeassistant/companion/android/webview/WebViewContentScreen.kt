package io.homeassistant.companion.android.webview

import android.Manifest
import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.util.compose.media.player.HAMediaPlayer
import io.homeassistant.companion.android.util.compose.webview.HAWebView
import kotlinx.coroutines.launch

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
    shouldAskForNotificationPermissionIfNeeded: Boolean,
    webViewInitialized: Boolean,
    onFullscreenClicked: (isFullscreen: Boolean) -> Unit,
    onDiscardNotificationPermission: () -> Unit,
    nightModeTheme: NightModeTheme? = null,
    statusBarColor: Color? = null,
    backgroundColor: Color? = null,
) {
    HATheme {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
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
        if (webViewInitialized && shouldAskForNotificationPermissionIfNeeded) {
            NotificationPermission(onDiscardNotificationPermission)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
private fun NotificationPermission(onDiscardNotificationPermission: () -> Unit) {
    val bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    val coroutineScope = rememberCoroutineScope()

    // Track whether the bottom sheet has been dismissed to completely remove it from composition.
    // This is necessary because Material 3's ModalBottomSheet creates a Dialog window that,
    // even when hidden, can block touch events to underlying views like the WebView.
    // By removing the composable entirely when dismissed (checking !isClosed), we ensure
    // the Dialog window is destroyed and the WebView remains fully interactive.
    var isClosed by remember { mutableStateOf(false) }

    fun closeSheet() {
        coroutineScope.launch {
            bottomSheetState.hide()
            isClosed = true
        }
    }

    // By default on lower API the bottom sheet won't be displayed
    @SuppressLint("InlinedApi")
    val notificationPermission = rememberPermissionState(
        permission = Manifest.permission.POST_NOTIFICATIONS,
        previewPermissionStatus = PermissionStatus.Denied(true),
        onPermissionResult = {
            closeSheet()
        },
    )

    if (notificationPermission.status is PermissionStatus.Denied && !isClosed) {
        HAModalBottomSheet(
            bottomSheetState = bottomSheetState,
            onDismissRequest = {
                closeSheet()
            },
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = HADimens.SPACE6)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE6),
            ) {
                Text(
                    text = stringResource(commonR.string.notification_permission_dialog_title),
                    style = HATextStyle.Headline,
                )
                Text(
                    text = stringResource(commonR.string.notification_permission_dialog_content),
                    style = HATextStyle.Body,
                )
                HAAccentButton(
                    text = stringResource(commonR.string.notification_permission_dialog_allow),
                    onClick = {
                        notificationPermission.launchPermissionRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                HAPlainButton(
                    text = stringResource(commonR.string.notification_permission_dialog_denied),
                    onClick = {
                        onDiscardNotificationPermission()
                        closeSheet()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = HADimens.SPACE6),
                )
            }
        }
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
        shouldAskForNotificationPermissionIfNeeded = false,
        webViewInitialized = true,
        customViewFromWebView = null,
        onFullscreenClicked = {},
        onDiscardNotificationPermission = {},
    )
}
