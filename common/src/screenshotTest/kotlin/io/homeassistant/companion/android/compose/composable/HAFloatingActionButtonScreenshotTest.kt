package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAFloatingActionButton
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HAFloatingActionButtonScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAFloatingActionButton variants`() {
        HAThemeForPreview {
            Column {
                ButtonVariant.entries.forEach { variant ->
                    Row {
                        HAFloatingActionButton(
                            icon = Icons.Default.Add,
                            variant = variant,
                            contentDescription = null,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}
