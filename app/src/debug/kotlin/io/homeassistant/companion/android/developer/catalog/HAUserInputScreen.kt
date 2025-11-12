package io.homeassistant.companion.android.developer.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.composable.rememberSelectedOption
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview

fun LazyListScope.catalogUserInputSection() {
    input()
    switches()
    radioGroupSection()
}

private fun LazyListScope.input() {
    catalogSection(title = "Input") {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            var value1 by remember { mutableStateOf("") }
            var value2 by remember { mutableStateOf("") }
            var value3 by remember { mutableStateOf("") }
            var value4 by remember { mutableStateOf("") }
            var value5 by remember { mutableStateOf("error") }
            var value6 by remember { mutableStateOf("super secret") }
            CatalogRow {
                HATextField(
                    value = value1,
                    onValueChange = { value1 = it },
                    trailingIcon = {
                        if (value1.isNotBlank()) {
                            IconButton(onClick = { value1 = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                )
                HATextField(
                    value = value2,
                    onValueChange = { value2 = it },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
                HATextField(
                    value = value3,
                    onValueChange = { value3 = it },
                    label = {
                        Text(
                            text = "Label",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
                HATextField(
                    value = value4,
                    onValueChange = { value4 = it },
                    label = {
                        Text(
                            text = "Label",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
                HATextField(
                    value = value5,
                    onValueChange = { value5 = it },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                        )
                    },
                    label = {
                        Text(
                            text = "Label",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    isError = value5.isNotBlank(),
                    supportingText = {
                        if (value5.isNotBlank()) {
                            Text(
                                text = "Supporting text",
                                style = HATextStyle.BodyMedium.copy(color = Color.Unspecified),
                            )
                        }
                    },
                )
                HATextField(
                    value = BIG_CONTENT,
                    enabled = false,
                    onValueChange = { },
                    label = {
                        Text(
                            text = "Label",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
                HATextField(
                    value = value6,
                    onValueChange = { value6 = it },
                    visualTransformation = PasswordVisualTransformation(),
                    label = {
                        Text(
                            text = "Password",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Placeholder",
                            style = HATextStyle.UserInput.copy(color = Color.Unspecified),
                        )
                    },
                )
            }
        }
    }
}

private fun LazyListScope.radioGroupSection() {
    catalogSection(title = "Radio group") {
        var selectedOption by rememberSelectedOption<String>()

        HARadioGroup(
            options = listOf(
                RadioOption(
                    "key1",
                    "Title",
                    "SubTitle",
                ),
                RadioOption(
                    "key2",
                    "Title2",
                ),
                RadioOption(
                    "key3",
                    "Title2",
                    enabled = false,
                ),
                RadioOption(
                    "key3",
                    "Very long text, to verifiy that nothing is broken when it is displayed within the bounds.",
                    enabled = false,
                ),
            ),
            onSelect = {
                selectedOption = it
            },
            selectionKey = selectedOption?.selectionKey,
        )
    }
}

private fun LazyListScope.switches() {
    catalogSection(title = "Switches") {
        CatalogRow {
            var isChecked by remember { mutableStateOf(false) }
            HASwitch(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                },
            )
            HASwitch(
                checked = !isChecked,
                onCheckedChange = {
                    isChecked = !it
                },
            )
        }
    }
}

@Preview(showBackground = true, device = TABLET)
@Composable
private fun PreviewHAUserInputScreen() {
    HAThemeForPreview {
        LazyColumn {
            catalogUserInputSection()
        }
    }
}
