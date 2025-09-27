package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

private class VariantProvider : PreviewParameterProvider<ButtonVariant> {
    override val values: Sequence<ButtonVariant> = ButtonVariant.entries.asSequence()
}

class HAButtonScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAAccentButton`(@PreviewParameter(VariantProvider::class) variant: ButtonVariant) {
        HAThemeForPreview {
            Row {
                HAAccentButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
                    size = ButtonSize.SMALL,
                )
                HAAccentButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
                    size = ButtonSize.MEDIUM,
                )
                HAAccentButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
                    size = ButtonSize.LARGE,
                )
                HAAccentButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAFilledButton`(@PreviewParameter(VariantProvider::class) variant: ButtonVariant) {
        HAThemeForPreview {
            Row {
                HAFilledButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
                    size = ButtonSize.SMALL,
                )
                HAFilledButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
                    size = ButtonSize.MEDIUM,
                )
                HAFilledButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
                    size = ButtonSize.LARGE,
                )
                HAFilledButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAPlainButton`(@PreviewParameter(VariantProvider::class) variant: ButtonVariant) {
        HAThemeForPreview {
            Row {
                HAPlainButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
                    size = ButtonSize.SMALL,
                )
                HAPlainButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
                    size = ButtonSize.MEDIUM,
                )
                HAPlainButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
                    size = ButtonSize.LARGE,
                )
                HAPlainButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}
