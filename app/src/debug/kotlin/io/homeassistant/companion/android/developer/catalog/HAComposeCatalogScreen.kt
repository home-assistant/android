package io.homeassistant.companion.android.developer.catalog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.composable.ButtonSize
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HAProgress
import io.homeassistant.companion.android.common.compose.composable.HARadioGroup
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.RadioOption
import io.homeassistant.companion.android.common.compose.composable.rememberSelectedOption
import io.homeassistant.companion.android.common.compose.theme.HASize
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme

private const val BIG_CONTENT =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

@Composable
fun HAComposeCatalogScreen() {
    HATheme {
        var currentVariant by remember { mutableStateOf(ButtonVariant.PRIMARY) }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.add(
                WindowInsets(
                    left = HASpacing.M,
                    top = HASpacing.M,
                    right = HASpacing.M,
                    bottom = HASpacing.M,
                ),
            ),
            topBar = {
                TopBar { currentVariant = it }
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = it,
                verticalArrangement = Arrangement.spacedBy(HASpacing.M),
            ) {
                buttonSection(variant = currentVariant, enabled = true)
                buttonSection(variant = currentVariant, enabled = false)
                buttonsWithIcon(variant = currentVariant)
                buttonsWithBigContent(variant = currentVariant)
                switches()
                radioGroupSection()
                textStyles()
                input()
                progress()
            }
        }
    }
}

@Composable
private fun VariantDropdownMenu(onVariantClick: (ButtonVariant) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.padding(HASpacing.M)) {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Select variant")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ButtonVariant.entries.forEach { variant ->
                DropdownMenuItem(
                    text = { Text(variant.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = { onVariantClick(variant) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onVariantClick: (ButtonVariant) -> Unit) {
    TopAppBar(
        title = { Text("HA Compose Catalog", style = HATextStyle.Headline) },
        actions = {
            VariantDropdownMenu(
                modifier = Modifier,
                onVariantClick = onVariantClick,
            )
        },
    )
}

@Composable
private fun CatalogRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(HASpacing.M),
        verticalArrangement = Arrangement.spacedBy(HASpacing.M),
    ) {
        content()
    }
}

private fun LazyListScope.catalogSection(title: String, content: @Composable () -> Unit) {
    item {
        Text(text = title, modifier = Modifier.padding(top = HASpacing.M), style = HATextStyle.Body)
    }
    item {
        content()
    }
}

private fun LazyListScope.buttonSection(variant: ButtonVariant, enabled: Boolean) {
    catalogSection(title = "Buttons ${if (enabled) "enabled" else "disabled"}") {
        CatalogRow {
            ButtonSize.entries.forEach {
                HAAccentButton(
                    text = "Label",
                    enabled = enabled,
                    onClick = {},
                    variant = variant,
                    size = it,
                )
            }
            ButtonSize.entries.forEach {
                HAFilledButton(
                    text = "Label",
                    enabled = enabled,
                    onClick = {},
                    variant = variant,
                    size = it,
                )
            }
            ButtonSize.entries.forEach {
                HAPlainButton(
                    text = "Label",
                    enabled = enabled,
                    onClick = {},
                    variant = variant,
                    size = it,
                )
            }
        }
    }
}

@Composable
private fun AddIcon() {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun LazyListScope.buttonsWithIcon(variant: ButtonVariant) {
    catalogSection(title = "Buttons with Icon") {
        CatalogRow {
            HAAccentButton(
                text = "Label",
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
            )
            HAFilledButton(
                text = "Label",
                onClick = {},
                variant = variant,
                suffix = { AddIcon() },
            )
            HAPlainButton(
                text = "Label",
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAAccentButton(
                text = "Label",
                onClick = {},
                enabled = false,
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
        }
    }
}

private fun LazyListScope.buttonsWithBigContent(variant: ButtonVariant) {
    catalogSection(title = "Button with big content") {
        CatalogRow {
            HAAccentButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAAccentButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
                maxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
            )
            HAFilledButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAFilledButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },

                maxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
            )
            HAPlainButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAPlainButton(
                text = BIG_CONTENT,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
                maxLines = 1,
                textOverflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LazyListScope.textStyles() {
    catalogSection(title = "Text Style") {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Headline",
                style = HATextStyle.Headline,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Body",
                style = HATextStyle.Body,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "BodyMedium",
                style = HATextStyle.BodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "UserInput",
                style = HATextStyle.UserInput,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Button",
                style = HATextStyle.Button,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
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

private fun LazyListScope.progress() {
    catalogSection(title = "Progress") {
        CatalogRow {
            HALoading(modifier = Modifier.size(HASize.X5L))
            var progress by remember { mutableFloatStateOf(0.1f) }
            val animatedProgress by
                animateFloatAsState(
                    targetValue = progress,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                )
            HAProgress(
                { animatedProgress },
                modifier = Modifier.clickable(
                    onClick = {
                        progress = 1f
                    },
                ),
            )
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
            HASwitch(checked = isChecked, onCheckedChange = {
                isChecked = it
            })
            HASwitch(checked = !isChecked, onCheckedChange = {
                isChecked = !it
            })
        }
    }
}

@Composable
@Preview(showBackground = true, heightDp = 2000, widthDp = 1000)
private fun HAComposeCatalogScreenPreview() {
    HAComposeCatalogScreen()
}
