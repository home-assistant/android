package io.homeassistant.companion.android.settings.url.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

class ExternalUrlViewScreenshotTest {
    @PreviewTest
    @Preview
    @Composable
    fun `ExternalUrlView with Cloud on`() {
        HomeAssistantAppTheme {
            ExternalUrlView(
                canUseCloud = true,
                useCloud = true,
                externalUrl = "https://home.example.com:8123/",
                onUseCloudToggle = {},
                onExternalUrlSaved = {},
            )
        }
    }

    @PreviewTest
    @Preview
    @Composable
    fun `ExternalUrlView with Cloud off`() {
        HomeAssistantAppTheme {
            ExternalUrlView(
                canUseCloud = true,
                useCloud = false,
                externalUrl = "https://home.example.com:8123/",
                onUseCloudToggle = {},
                onExternalUrlSaved = {},
            )
        }
    }

    @PreviewTest
    @Preview
    @Composable
    fun `ExternalUrlView without Cloud`() {
        HomeAssistantAppTheme {
            ExternalUrlView(
                canUseCloud = false,
                useCloud = false,
                externalUrl = "https://home.example.com:8123/",
                onUseCloudToggle = {},
                onExternalUrlSaved = {},
            )
        }
    }
}
