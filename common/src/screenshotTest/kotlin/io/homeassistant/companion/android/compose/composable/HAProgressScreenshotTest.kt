package io.homeassistant.companion.android.compose.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAProgress
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

private class ProgressProvider : PreviewParameterProvider<Float> {
    override val values: Sequence<Float> = sequenceOf(0f, 0.3f, 0.5f, 1f)
}

class HAProgressScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HALoading`() {
        HAThemeForPreview {
            HALoading(modifier = Modifier)
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAProgress for fixed value`(@PreviewParameter(ProgressProvider::class) progress: Float) {
        HAThemeForPreview {
            HAProgress({ progress })
        }
    }
}
