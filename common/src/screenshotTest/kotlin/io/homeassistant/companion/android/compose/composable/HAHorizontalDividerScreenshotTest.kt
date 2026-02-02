package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HAHorizontalDividerScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAHorizontalDivider default`() {
        HAThemeForPreview {
            HAHorizontalDivider(modifier = Modifier.width(300.dp).padding(HADimens.SPACE3))
        }
    }
}
