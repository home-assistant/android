package io.homeassistant.companion.android.onboarding.connection

import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.compose.composable.HAWebView
import io.homeassistant.companion.android.loading.LoadingScreen
import io.homeassistant.companion.android.onboarding.R
import timber.log.Timber

@VisibleForTesting
const val CONNECTION_SCREEN_TAG = "connection_screen"

@VisibleForTesting
const val CONNECTION_SCREEN_ERROR_PLACEHOLDER_TAG = "connection_screen_error"

private val ICON_SIZE = 64.dp

@Composable
internal fun ConnectionScreen(onBackClick: () -> Unit, viewModel: ConnectionViewModel, modifier: Modifier = Modifier) {
    val url by viewModel.urlFlow.collectAsState()
    val isLoading by viewModel.isLoadingFlow.collectAsState()
    val error by viewModel.errorFlow.collectAsState()
    val isError = error != null

    ConnectionScreen(
        url = url,
        isLoading = isLoading,
        isError = isError,
        webViewClient = viewModel.webViewClient,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
internal fun ConnectionScreen(
    url: String?,
    isLoading: Boolean,
    isError: Boolean,
    webViewClient: WebViewClient,
    onBackClick: () -> Unit,
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
        if (!isError) {
            url?.let {
                HAWebView(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    configure = {
                        this.webViewClient = webViewClient
                        loadUrl(url)
                    },
                    onBackPressed = onBackClick,
                )
            } ?: Timber.i("ConnectionScreen: url is null")
        } else {
            ErrorPlaceholder()
        }

        if (isLoading) {
            LoadingScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * This placeholder is used to hide the ugly error screen from the webview, while a toast is displayed
 * before leaving this screen.
 */
@Composable
private fun ErrorPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(CONNECTION_SCREEN_ERROR_PLACEHOLDER_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_home_assistant_branding),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

@HAPreviews
@Composable
private fun ConnectionScreenPreview() {
    HAThemeForPreview {
        ConnectionScreen(
            url = "https://www.home-assistant.io",
            isLoading = false,
            isError = false,
            webViewClient = WebViewClient(),
            onBackClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
