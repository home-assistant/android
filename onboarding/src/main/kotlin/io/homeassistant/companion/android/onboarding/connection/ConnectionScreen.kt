package io.homeassistant.companion.android.onboarding.connection

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
import androidx.compose.ui.Modifier
import io.homeassistant.companion.android.compose.composable.HAWebView
import io.homeassistant.companion.android.compose.composable.settings
import io.homeassistant.companion.android.theme.LocalHAColorScheme

@Composable
internal fun ConnectionScreen(viewModel: ConnectionViewModel, modifier: Modifier = Modifier) {
    ConnectionScreen(url = viewModel.url, modifier = modifier)
}

@Composable
internal fun ConnectionScreen(url: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding(),
                )
                .background(LocalHAColorScheme.current.launchScreenBackground),
        )
        HAWebView(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            configure = {
                loadUrl(url)
                settings {
                }
            },
        )
    }
}
