package io.homeassistant.companion.android.loading

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

class LoadingScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `LoadingScreen`() {
        HAThemeForPreview {
            LoadingScreen(modifier = Modifier)
        }
    }

    @PreviewTest
    @HAPreviews
    @Preview(
        name = "small_phone",
        device = "spec:width=120dp,height=220dp,dpi=480,orientation=portrait",
        group = "phone",
    ) // Very small width to see the branding on two lines, and short enough that the icon has to
    // rise above the center to leave room for the branding
    @Composable
    fun `LoadingScreen with branding`() {
        HAThemeForPreview {
            LoadingScreen(modifier = Modifier, showBrand = true)
        }
    }
}
