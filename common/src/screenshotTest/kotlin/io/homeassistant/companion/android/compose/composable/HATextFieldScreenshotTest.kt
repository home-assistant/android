package io.homeassistant.companion.android.compose.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

class HATextFieldScreenshotTest {

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HATextField`() {
        HAThemeForPreview {
            Column {
                HATextFieldForTest("Hello")
                HATextFieldForTest("")
            }
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HATextField with label`() {
        HAThemeForPreview {
            Column {
                HATextFieldForTest("Hello", label = "Label")
                HATextFieldForTest("", label = "Label")
            }
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HATextField with placeholder`() {
        HAThemeForPreview {
            Column {
                HATextFieldForTest("Value write on top of placeholder", placeholder = "Placeholder")
                HATextFieldForTest("", placeholder = "Placeholder")
            }
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HATextField with error`() {
        HAThemeForPreview {
            Column {
                HATextFieldForTest("Hello", label = "Label", errorText = "Error with label")
                HATextFieldForTest("Hello", errorText = "Error without label")
            }
        }
    }

    @PreviewLightDark
    @PreviewTest
    @Composable
    fun `HATextField disabled`() {
        HAThemeForPreview {
            Column {
                HATextFieldForTest("Hello", label = "Label", enabled = false)
                HATextFieldForTest("Hello", errorText = "Error without label", enabled = false)
                HATextFieldForTest("", placeholder = "Placeholder", enabled = false)
            }
        }
    }

    @Composable
    private fun HATextFieldForTest(
        value: String,
        enabled: Boolean = true,
        label: String? = null,
        placeholder: String? = null,
        errorText: String? = null,
    ) {
        HATextField(
            // For some reason in test we need to fix the height and to avoid weird cut we fix it to a big value
            modifier = Modifier.height(120.dp),
            enabled = enabled,
            value = value,
            onValueChange = {},
            label = label?.let { { Text(label) } },
            placeholder = placeholder?.let { { Text(placeholder) } },
            isError = errorText != null,
            supportingText = errorText?.let {
                {
                    Text(errorText)
                }
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                    modifier = Modifier.padding(
                        end = HADimens.SPACE4,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                }
            },
        )
    }
}
