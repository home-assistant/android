package io.homeassistant.companion.android.settings.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class SettingsSubheaderScreenshotTest {

    @Preview
    @Composable
    fun `SettingsSubheader with equal padding`() {
        SettingsSubheader("Attributes")
    }

    @Preview
    @Composable
    fun `SettingsSubheader with additional icon padding`() {
        SettingsSubheader(
            text = "Health Connect sensors",
            modifier = Modifier.background(Color.Red).width(400.dp),
            textPadding = SettingsSubheaderDefaults.TextWithIconRowPadding,
        )
    }
}
