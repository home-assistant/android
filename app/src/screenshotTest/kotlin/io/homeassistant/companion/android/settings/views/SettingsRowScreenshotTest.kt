package io.homeassistant.companion.android.settings.views

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial

class SettingsRowScreenshotTest {

    @PreviewTest
    @Preview
    @Composable
    fun `SettingsRow with only title and subtitle`() {
        SettingsRow(
            primaryText = "Title",
            secondaryText = "Subtitle",
            icon = null,
            onClicked = {},
        )
    }

    @PreviewTest
    @Preview
    @Composable
    fun `SettingsRow with custom icon composable`() {
        SettingsRow(
            primaryText = "Title",
            secondaryText = "Subtitle",
            icon = {
                Spacer(
                    Modifier
                        .border(width = 1.dp, color = Color.Magenta)
                        .size(width = 56.dp, height = 16.dp),
                )
            },
            onClicked = {},
        )
    }

    @PreviewTest
    @Preview
    @Composable
    fun `SettingsRow with MDI icon on`() {
        SettingsRow(
            primaryText = "Title",
            secondaryText = "Subtitle",
            mdiIcon = CommunityMaterial.Icon.cmd_ab_testing,
            enabled = true,
            onClicked = {},
        )
    }

    @PreviewTest
    @Preview
    @Composable
    fun `SettingsRow with MDI icon off`() {
        SettingsRow(
            primaryText = "Title",
            secondaryText = "Subtitle",
            mdiIcon = CommunityMaterial.Icon.cmd_ab_testing,
            enabled = false,
            onClicked = {},
        )
    }
}
