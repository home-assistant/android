package io.homeassistant.companion.android.settings.wear.views

import android.content.Intent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.safeTopWindowInsets

const val WEAR_DOCS_LINK = "https://companion.home-assistant.io/docs/wear-os/"

@Composable
fun LoadSettingsHomeView(
    settingsWearViewModel: SettingsWearViewModel,
    deviceName: String,
    loginWearOs: () -> Unit,
    onStartBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeAssistantAppTheme {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = SettingsWearMainView.LANDING,
            modifier = modifier,
        ) {
            composable(SettingsWearMainView.FAVORITES) {
                LoadWearFavoritesSettings(
                    settingsWearViewModel = settingsWearViewModel,
                    onBackClicked = { navController.navigateUp() },
                    events = settingsWearViewModel.resultSnackbar,
                )
            }
            composable(SettingsWearMainView.LANDING) {
                val hasData by settingsWearViewModel.hasData.collectAsState()
                val isAuthenticated by settingsWearViewModel.isAuthenticated.collectAsState()

                SettingWearLandingView(
                    deviceName = deviceName,
                    hasData = hasData,
                    isAuthed = isAuthenticated,
                    navigateFavorites = { navController.navigate(SettingsWearMainView.FAVORITES) },
                    navigateTemplateTile = { navController.navigate(SettingsWearMainView.TEMPLATES) },
                    loginWearOs = loginWearOs,
                    onBackClicked = onStartBackClicked,
                    events = settingsWearViewModel.resultSnackbar,
                )
            }
            composable(SettingsWearMainView.TEMPLATES) {
                SettingsWearTemplateTileList(
                    templateTiles = settingsWearViewModel.templateTiles,
                    onTemplateTileClicked = { tileId ->
                        navController.navigate(SettingsWearMainView.TEMPLATE_TILE.format(tileId))
                    },
                    onBackClicked = {
                        navController.navigateUp()
                    },
                )
            }
            composable(
                route = SettingsWearMainView.TEMPLATE_TILE.format("{tileId}"),
                arguments = listOf(navArgument("tileId") { type = NavType.IntType }),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments?.getInt("tileId")
                val templateTile = settingsWearViewModel.templateTiles[tileId]
                val renderedTemplate = settingsWearViewModel.templateTilesRenderedTemplates[tileId]

                templateTile?.let {
                    SettingsWearTemplateTile(
                        template = it.template,
                        renderedTemplate = renderedTemplate ?: "",
                        refreshInterval = it.refreshInterval,
                        onContentChanged = { templateContent ->
                            settingsWearViewModel.setTemplateTileContent(tileId!!, templateContent)
                            settingsWearViewModel.sendTemplateTileInfo()
                        },
                        onRefreshIntervalChanged = { refreshInterval ->
                            settingsWearViewModel.setTemplateTileRefreshInterval(tileId!!, refreshInterval)
                            settingsWearViewModel.sendTemplateTileInfo()
                        },
                        onBackClicked = {
                            navController.navigateUp()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsWearTopAppBar(
    title: @Composable () -> Unit,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    docsLink: String? = null,
) {
    val context = LocalContext.current
    HATopBar(
        title = title,
        modifier = modifier.windowInsetsPadding(safeTopWindowInsets()),
        onBackClick = onBackClicked,
        onHelpClick = if (!docsLink.isNullOrBlank()) {
            {
                val intent = Intent(Intent.ACTION_VIEW, docsLink.toUri())
                context.startActivity(intent)
            }
        } else {
            null
        },
    )
}
