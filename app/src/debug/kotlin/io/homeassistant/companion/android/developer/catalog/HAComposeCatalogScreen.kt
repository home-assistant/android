package io.homeassistant.companion.android.developer.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme

private sealed class CatalogScreen(val title: String, val icon: ImageVector) {
    object ButtonsAndIndicators : CatalogScreen("Buttons & Indicators", Icons.Default.TouchApp)
    object UserInput : CatalogScreen("User Input", Icons.Default.Edit)
    object TextAndBanners : CatalogScreen(
        "Text & Banners",
        Icons.AutoMirrored.Filled.Article,
    )
}

@Composable
fun HAComposeCatalogScreen() {
    val screens = listOf(
        CatalogScreen.ButtonsAndIndicators,
        CatalogScreen.UserInput,
        CatalogScreen.TextAndBanners,
    )
    var currentScreen by remember { mutableStateOf<CatalogScreen>(CatalogScreen.ButtonsAndIndicators) }
    var currentVariant by remember { mutableStateOf(ButtonVariant.PRIMARY) }

    HATheme {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.add(
                WindowInsets(
                    left = HADimens.SPACE4,
                    top = HADimens.SPACE4,
                    right = HADimens.SPACE4,
                    bottom = HADimens.SPACE4,
                ),
            ),
            topBar = {
                TopBar { currentVariant = it }
            },
            bottomBar = {
                NavigationBar {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                        )
                    }
                }
            },
        ) { scaffoldPadding ->
            val layoutDirection = LocalLayoutDirection.current
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = scaffoldPadding.calculateLeftPadding(LocalLayoutDirection.current),
                    top = scaffoldPadding.calculateTopPadding(),
                    end = scaffoldPadding.calculateRightPadding(layoutDirection),
                    bottom = scaffoldPadding.calculateBottomPadding() + HADimens.SPACE4,
                ),
                verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
            ) {
                when (currentScreen) {
                    CatalogScreen.ButtonsAndIndicators -> catalogButtonsAndIndicatorsSection(currentVariant)
                    CatalogScreen.UserInput -> catalogUserInputSection()
                    CatalogScreen.TextAndBanners -> catalogTextAndBannersSection()
                }
            }
        }
    }
}

@Composable
private fun VariantDropdownMenu(onVariantClick: (ButtonVariant) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.padding(HADimens.SPACE4)) {
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
@Preview(showBackground = true, device = TABLET)
private fun HAComposeCatalogScreenPreview() {
    HAComposeCatalogScreen()
}
