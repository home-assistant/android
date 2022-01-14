package io.homeassistant.companion.android.settings.wear.views

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel

@Composable
fun LoadSettingsHomeView(
    settingsWearViewModel: SettingsWearViewModel,
    deviceName: String,
    loginWearOs: () -> Unit
) {
    MdcTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = SettingsWearMainView.LANDING) {
            composable(SettingsWearMainView.FAVORITES) {
                LoadWearFavoritesSettings(
                    settingsWearViewModel
                )
            }
            composable(SettingsWearMainView.LANDING) {
                SettingWearLandingView(
                    deviceName = deviceName,
                    hasData = settingsWearViewModel.hasData.value,
                    isAuthed = settingsWearViewModel.isAuthenticated.value,
                    navigateFavorites = { navController.navigate(SettingsWearMainView.FAVORITES) },
                    navigateTemplateTile = { navController.navigate(SettingsWearMainView.TEMPLATE) },
                    loginWearOs = loginWearOs
                )
            }
            composable(SettingsWearMainView.TEMPLATE) {
                SettingsWearTemplateTile(
                    template = settingsWearViewModel.templateTileContent.value,
                    renderedTemplate = settingsWearViewModel.templateTileContentRendered.value,
                    refreshInterval = settingsWearViewModel.templateTileRefreshInterval.value,
                    onContentChanged = {
                        settingsWearViewModel.setTemplateContent(it)
                        settingsWearViewModel.sendTemplateTileInfo()
                    },
                    onRefreshIntervalChanged = {
                        settingsWearViewModel.templateTileRefreshInterval.value = it
                        settingsWearViewModel.sendTemplateTileInfo()
                    },
                    onBackClicked = {
                        navController.navigateUp()
                    }
                )
            }
        }
    }
}
