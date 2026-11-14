package io.homeassistant.companion.android.settings.network

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews
import kotlin.time.Duration.Companion.seconds

class RequestTimeoutScreenshotTest {
    @PreviewTest
    @HAPreviews
    @Composable
    fun `Request timeout defaults`() {
        HAThemeForPreview {
            RequestTimeoutContent(RequestTimeoutUiState(timeout = 30.seconds), onSelect = {}, onRetry = {})
        }
    }
}
