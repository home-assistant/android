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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.developer.catalog.composable.ButtonVariant
import io.homeassistant.companion.android.developer.catalog.composable.HAAccentButton
import io.homeassistant.companion.android.developer.catalog.composable.HAFilledButton
import io.homeassistant.companion.android.developer.catalog.composable.HAPlainButton
import io.homeassistant.companion.android.developer.catalog.theme.HATextStyle
import io.homeassistant.companion.android.developer.catalog.theme.HATheme

@Composable
private fun VariantDropdownMenu(onVariantClick: (ButtonVariant) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .padding(16.dp),
    ) {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Primary") },
                onClick = { onVariantClick(ButtonVariant.PRIMARY) },
            )
            DropdownMenuItem(
                text = { Text("Neutral") },
                onClick = { onVariantClick(ButtonVariant.NEUTRAL) },
            )
            DropdownMenuItem(
                text = { Text("Danger") },
                onClick = { onVariantClick(ButtonVariant.DANGER) },
            )
            DropdownMenuItem(
                text = { Text("Warning") },
                onClick = { onVariantClick(ButtonVariant.WARNING) },
            )
            DropdownMenuItem(
                text = { Text("Success") },
                onClick = { onVariantClick(ButtonVariant.SUCCESS) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HAComposeCatalogScreen() {
    HATheme {
        var currentVariant by remember { mutableStateOf(ButtonVariant.PRIMARY) }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.add(
                WindowInsets(
                    left = 16.dp,
                    top = 16.dp,
                    right = 16.dp,
                    bottom = 16.dp,
                ),
            ),
            topBar = {
                TopAppBar(
                    title = { Text("HA Compose Catalog") },
                    actions = {
                        VariantDropdownMenu(
                            modifier = Modifier,
                            onVariantClick = { currentVariant = it },
                        )
                    },
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = it,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text("Buttons enabled", Modifier.padding(top = 16.dp))
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HAAccentButton(
                            text = "Label",
                            onClick = {},
                            variant = currentVariant,
                        )
                        HAFilledButton(
                            text = "Label",
                            onClick = {},
                            variant = currentVariant,
                        )
                        HAPlainButton(
                            text = "Label",
                            onClick = {},
                            variant = currentVariant,
                        )
                    }
                }
                item {
                    Text("Buttons disbaled", Modifier.padding(top = 16.dp))
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HAAccentButton(
                            text = "Label",
                            onClick = {},
                            enabled = false,
                            variant = currentVariant,
                        )
                        HAFilledButton(
                            text = "Label",
                            onClick = {},
                            enabled = false,
                            variant = currentVariant,
                        )
                        HAPlainButton(
                            text = "Label",
                            onClick = {},
                            enabled = false,
                            variant = currentVariant,
                        )
                    }
                }
                item {
                    Text("Text Style", Modifier.padding(top = 16.dp))
                }
                item {
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
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun HAComposeCatalogScreenPreview() {
    HAComposeCatalogScreen()
}
