package io.homeassistant.companion.android.settings.views

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.settings.SettingsWearViewModel

@Composable
fun LoadSettingsHomeView(
    settingsWearViewModel: SettingsWearViewModel,
    deviceName: String
) {
    MdcTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = SettingsWearMainView.LANDING) {
            composable(SettingsWearMainView.FAVORITES) {
                LoadWearFavoritesSettings(
                    settingsWearViewModel.entities,
                    settingsWearViewModel.favoriteEntityIds.toList()
                ) { b: Boolean, s: String ->
                    settingsWearViewModel.onEntitySelected(
                        b,
                        s
                    )
                }
            }
            composable(SettingsWearMainView.LANDING) {
                SettingWearLandingView(deviceName) {
                    navController.navigate(SettingsWearMainView.FAVORITES)
                }
            }
        }
    }
}
