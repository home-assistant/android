package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Plus
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAFloatingActionButton
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HAFloatingActionButtonScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAFloatingActionButton variants`() {
        HAThemeForPreview {
            Column {
                ButtonVariant.entries.forEach { variant ->
                    Row {
                        HAFloatingActionButton(
                            icon = Mdi.Plus.rememberImageVector(),
                            variant = variant,
                            contentDescription = null,
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}
