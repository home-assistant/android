package io.homeassistant.companion.android.compose

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme

/**
 * Small Wrapper around [HATheme] to handle the background color for screenshots, since the color of the background
 * of the app is handle by [HAApp], to test individual screen we need to set the background color manually.
 */
@Composable
fun HAThemeScreenshot(content: @Composable () -> Unit) {
    HATheme {
        Scaffold(containerColor = LocalHAColorScheme.current.colorSurfaceDefault) {
            content()
        }
    }
}
