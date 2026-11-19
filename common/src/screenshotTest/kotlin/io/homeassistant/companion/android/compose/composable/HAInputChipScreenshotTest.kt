package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Close
import io.github.timoptr.mdiicons.generated.Plus
import io.github.timoptr.mdiicons.rememberImageVector
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
                    trailingIcon = Mdi.Plus.rememberImageVector(),
                    trailingIconContentDescription = null,
                )
                HAInputChip(
                    text = "selected with a quite significant long name",
                    onClick = {},
                    selected = true,
                    trailingIcon = Mdi.Close.rememberImageVector(),
                    trailingIconContentDescription = null,
                )
                HAInputChip(text = "without icon", onClick = {})
                HAInputChip(
                    text = "disabled",
                    onClick = {},
                    enabled = false,
                    trailingIcon = Mdi.Plus.rememberImageVector(),
                    trailingIconContentDescription = null,
                )
            }
        }
    }
}
