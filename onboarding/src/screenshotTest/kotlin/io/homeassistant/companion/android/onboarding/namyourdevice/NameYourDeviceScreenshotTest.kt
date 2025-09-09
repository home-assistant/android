package io.homeassistant.companion.android.onboarding.namyourdevice

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews
import io.homeassistant.companion.android.onboarding.nameyourdevice.NameYourDeviceScreen

class NameYourDeviceScreenshotTest {
    @PreviewTest
    @HAPreviews
    @Composable
    fun `NameYourDevice empty`() {
        HAThemeForPreview {
            NameYourDeviceScreen(
                onBackClick = {},
                deviceName = "",
                onDeviceNameChange = {},
                saveClickable = false,
                onSaveClick = {},
                onHelpClick = {},
            )
        }
    }

    @PreviewTest
    @HAPreviews
    @Composable
    fun `NameYourDevice with a name`() {
        HAThemeForPreview {
            NameYourDeviceScreen(
                onBackClick = {},
                deviceName = "Superman",
                onDeviceNameChange = {},
                saveClickable = true,
                onSaveClick = {},
                onHelpClick = {},
            )
        }
    }
}
