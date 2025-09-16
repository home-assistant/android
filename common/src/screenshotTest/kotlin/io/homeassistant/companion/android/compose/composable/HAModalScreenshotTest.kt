@file:OptIn(ExperimentalMaterial3Api::class)

package io.homeassistant.companion.android.compose.composable

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Density
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HAModalScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAModalBottomSheet expanded`() {
        HAModalBottomSheetTest(sheetValue = Expanded)
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAModalBottomSheet hidden`() {
        HAModalBottomSheetTest(sheetValue = Hidden)
    }

    @Composable
    private fun HAModalBottomSheetTest(sheetValue: SheetValue) {
        HAThemeForPreview {
            HAModalBottomSheet(
                bottomSheetState = SheetState(
                    skipPartiallyExpanded = false,
                    Density(LocalDensity.current.density),
                    initialValue = sheetValue,
                ),
            ) {
                Text("Hello 👋", style = HATextStyle.Headline)
            }
        }
    }
}
