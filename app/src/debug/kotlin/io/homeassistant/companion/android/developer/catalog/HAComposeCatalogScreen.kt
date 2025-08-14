package io.homeassistant.companion.android.developer.catalog

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HASpacing
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme

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
                // Max size buttons
                textStyles()
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
        title = { Text("HA Compose Catalog") },
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
        Text(text = title, modifier = Modifier.padding(top = HASpacing.M))
    }
    item {
        content()
    }
}

private fun LazyListScope.buttonSection(variant: ButtonVariant, enabled: Boolean) {
    catalogSection(title = "Buttons ${if (enabled) "enabled" else "disabled"}") {
        CatalogRow {
            HAAccentButton(
                text = "Label",
                enabled = enabled,
                onClick = {},
                variant = variant,
            )
            HAFilledButton(
                text = "Label",
                enabled = enabled,
                onClick = {},
                variant = variant,
            )
            HAPlainButton(
                text = "Label",
                enabled = enabled,
                onClick = {},
                variant = variant,
            )
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
    val content = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."

    catalogSection(title = "Button with big content") {
        CatalogRow {
            HAAccentButton(
                text = content,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAFilledButton(
                text = content,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
            )
            HAPlainButton(
                text = content,
                onClick = {},
                variant = variant,
                prefix = { AddIcon() },
                suffix = { AddIcon() },
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

@Composable
@Preview(showBackground = true, heightDp = 1500)
private fun HAComposeCatalogScreenPreview() {
    HAComposeCatalogScreen()
}
