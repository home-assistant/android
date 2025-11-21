package io.homeassistant.companion.android.onboarding.localfirst

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.compose.HAPreviews

class LocalFirstScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `LocalFirstContent empty`() {
        HAThemeForPreview {
            LocalFirstScreen(onNextClick = {})
        }
    }
}
