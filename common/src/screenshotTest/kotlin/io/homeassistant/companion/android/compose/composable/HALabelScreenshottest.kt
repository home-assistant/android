package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HABanner
import io.homeassistant.companion.android.common.compose.composable.HAHint
import io.homeassistant.companion.android.common.compose.composable.HALabel
import io.homeassistant.companion.android.common.compose.composable.HASettingsCard
import io.homeassistant.companion.android.common.compose.composable.LabelSize
import io.homeassistant.companion.android.common.compose.composable.LabelVariant
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HALabelScreenshottest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HALabel variants`() {
        HAThemeForPreview {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                LabelVariant.entries.forEach { variant ->
                    HALabel(text = "Label", variant = variant)
                }
                LabelVariant.entries.forEach { variant ->
                    HALabel(text = "Label", variant = variant, size = LabelSize.SMALL)
                }
            }
        }
    }
}
