package io.homeassistant.companion.android.onboarding.connection

import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HAWebView
import io.homeassistant.companion.android.loading.LoadingScreen

@VisibleForTesting const val CONNECTION_SCREEN_TAG = "connection_screen"

@Composable
internal fun ConnectionScreen(
    onBackPressed: () -> Unit,
    viewModel: ConnectionViewModel,
    modifier: Modifier = Modifier,
) {
    val url by viewModel.urlFlow.collectAsState()
    val isLoading by viewModel.isLoadingFlow.collectAsState()

    ConnectionScreen(
        url = url,
        isLoading = isLoading,
        webViewClient = viewModel.webViewClient,
        onBackPressed = onBackPressed,
        modifier = modifier,
    )
}

@Composable
internal fun ConnectionScreen(
    url: String?,
    isLoading: Boolean,
    webViewClient: WebViewClient,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.testTag(CONNECTION_SCREEN_TAG)) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
                )
                .background(LocalHAColorScheme.current.colorSurfaceDefault),
        )
        url?.let {
            HAWebView(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                configure = {
                    this.webViewClient = webViewClient
                    loadUrl(url)
                },
                onBackPressed = onBackPressed,
            )
        } ?: FailFast.fail { "ConnectionScreen: url is null" }
        if (isLoading) {
            LoadingScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

@HAPreviews
@Composable
private fun ConnectionScreenPreview() {
    HAThemeForPreview {
        ConnectionScreen(
            url = "https://www.home-assistant.io",
            isLoading = false,
            webViewClient = WebViewClient(),
            onBackPressed = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
