package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HATheme

private class VariantProvider : PreviewParameterProvider<ButtonVariant> {
    override val values: Sequence<ButtonVariant> = ButtonVariant.entries.asSequence()
}

class HAButtonScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAAccentButton`(@PreviewParameter(VariantProvider::class) variant: ButtonVariant) {
        HATheme {
            Row {
                HAAccentButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
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
        HATheme {
            Row {
                HAFilledButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
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
        HATheme {
            Row {
                HAPlainButton(
                    variant = variant,
                    text = "Label",
                    onClick = {},
                    enabled = true,
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
