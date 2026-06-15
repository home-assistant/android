package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HACheckboxScreenshotTest {
    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Simple HACheckbox`() {
        HAThemeForPreview {
            Row {
                for (enabled in listOf(true, false)) {
                    for (checked in listOf(true, false)) {
                        HACheckbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    }
                }
            }
        }
    }
}
