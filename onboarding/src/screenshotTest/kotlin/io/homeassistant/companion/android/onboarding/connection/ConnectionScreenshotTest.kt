package io.homeassistant.companion.android.onboarding.connection

import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews

class ConnectionScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionScreen loading`() {
        HAThemeForPreview {
            ConnectionScreen(
                url = "https://www.example.com",
                isLoading = true,
                webViewClient = WebViewClient(),
                onBackPressed = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ConnectionScreen loaded`() {
        HAThemeForPreview {
            ConnectionScreen(
                url = "https://www.example.com",
                isLoading = false,
                webViewClient = WebViewClient(),
                onBackPressed = {},
            )
        }
    }
}
