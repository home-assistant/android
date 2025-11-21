package io.homeassistant.companion.android.onboarding.manualserver

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews

class ManualServerScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ManualServerScreen empty`() {
        HAThemeForPreview {
            ManualServerScreen(
                isServerUrlValid = false,
                onBackClick = {},
                onConnectClick = {},
                onHelpClick = {},
                onServerUrlChange = {},
                serverUrl = "",
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ManualServerScreen with wrong url`() {
        HAThemeForPreview {
            ManualServerScreen(
                isServerUrlValid = false,
                onBackClick = {},
                onConnectClick = {},
                onHelpClick = {},
                onServerUrlChange = {},
                serverUrl = "hello://world.com",
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `ManualServerScreen with valid url`() {
        HAThemeForPreview {
            ManualServerScreen(
                isServerUrlValid = true,
                onBackClick = {},
                onConnectClick = {},
                onHelpClick = {},
                onServerUrlChange = {},
                serverUrl = "http://world.com",
            )
        }
    }
}
