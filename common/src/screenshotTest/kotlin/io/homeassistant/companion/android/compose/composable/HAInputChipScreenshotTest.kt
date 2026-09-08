package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HAInputChip
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HAInputChipScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAInputChip states`() {
        HAThemeForPreview {
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2)) {
                HAInputChip(
                    text = "unselected",
                    onClick = {},
                    trailingIcon = Icons.Default.Add,
                    trailingIconContentDescription = null,
                )
                HAInputChip(
                    text = "selected with a quite significant long name",
                    onClick = {},
                    selected = true,
                    trailingIcon = Icons.Default.Close,
                    trailingIconContentDescription = null,
                )
                HAInputChip(text = "without icon", onClick = {})
                HAInputChip(
                    text = "disabled",
                    onClick = {},
                    enabled = false,
                    trailingIcon = Icons.Default.Add,
                    trailingIconContentDescription = null,
                )
            }
        }
    }
}
