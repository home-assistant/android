package io.homeassistant.companion.android.compose.composable

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HASettingsCardScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `Simple HASettingsCard with content`() {
        HAThemeForPreview {
            HASettingsCard {
                Text(text = "Settings card content", style = HATextStyle.Body)
            }
        }
    }
}
