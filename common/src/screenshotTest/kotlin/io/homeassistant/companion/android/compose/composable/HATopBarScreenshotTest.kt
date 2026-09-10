package io.homeassistant.companion.android.compose.composable

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HATopBarScreenshotTest {

    @Preview(name = "LTR")
    @Preview(name = "RTL", locale = "ar")
    @PreviewTest
    @Composable
    fun `HATopBar with back and help`() {
        HAThemeForPreview {
            HATopBar(
                title = { Text("Title", style = HATextStyle.Headline) },
                onBackClick = {},
                onHelpClick = {},
            )
        }
    }

    @Preview(name = "LTR")
    @Preview(name = "RTL", locale = "ar")
    @PreviewTest
    @Composable
    fun `HATopBar with close`() {
        HAThemeForPreview {
            HATopBar(
                title = { Text("Title", style = HATextStyle.Headline) },
                onCloseClick = {},
            )
        }
    }
}
