package io.homeassistant.companion.android.settings.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class SettingsSubheaderScreenshotTest {

    @Preview
    @Composable
    fun `SettingsSubheader with equal padding`() {
        SettingsSubheader("Attributes")
    }

    @Preview
    @Composable
    fun `SettingsSubheader with additional icon padding`() {
        SettingsSubheader("Health Connect sensors", paddingForIcon = true)
    }
}
