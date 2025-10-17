package io.homeassistant.companion.android.compose.composable

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HADetails
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HADetailsScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HADetails collapsed`() {
        HAThemeForPreview {
            HADetails("Hello world", defaultExpanded = false) {
                Text("This text should not be displayed")
            }
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HADetails expanded`() {
        HAThemeForPreview {
            HADetails("Hello world", defaultExpanded = true) {
                Text("Nice little text", style = HATextStyle.Body)
            }
        }
    }
}
