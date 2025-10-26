package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HAButtonScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAAccentButton variants`() {
        HAThemeForPreview {
            Column {
                ButtonVariant.entries.forEach { variant ->
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
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAIconButton variants`() {
        HAThemeForPreview {
            Column {
                ButtonVariant.entries.forEach { variant ->
                    HAIconButton(
                        icon = Icons.Default.AccountBox,
                        variant = variant,
                        contentDescription = null,
                        onClick = {},
                        enabled = true,
                    )
                    HAIconButton(
                        icon = Icons.Default.AccountBox,
                        variant = variant,
                        contentDescription = null,
                        onClick = {},
                        enabled = false,
                    )
                }
            }
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAFilledButton variants`() {
        HAThemeForPreview {
            Column {
                ButtonVariant.entries.forEach { variant ->
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
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HAPlainButton variants`() {
        HAThemeForPreview {
            Column {
                ButtonVariant.entries.forEach { variant ->
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
    }
}
