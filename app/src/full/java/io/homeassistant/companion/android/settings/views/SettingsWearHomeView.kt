package io.homeassistant.companion.android.settings.views

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.material.composethemeadapter.MdcTheme
import io.homeassistant.companion.android.settings.SettingsWearViewModel

@Composable
fun LoadSettingsHomeView(
    settingsWearViewModel: SettingsWearViewModel,
    deviceName: String,
    activity: Activity
) {
    val context = LocalContext.current
    MdcTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = SettingsWearMainView.LANDING) {
            composable(SettingsWearMainView.FAVORITES) {
                LoadWearFavoritesSettings(
                    settingsWearViewModel.entities,
                    settingsWearViewModel.favoriteEntityIds.toList(),
                    { b: Boolean, s: String, a: Activity -> settingsWearViewModel.onEntitySelected(b, s, a) },
                    {
                        settingsWearViewModel.favoriteEntityIds.toList().contains(
                            settingsWearViewModel.favoriteEntityIds.toList()[it]
                        )
                    },
                    activity
                )
            }
            composable(SettingsWearMainView.LANDING) {
                SettingWearLandingView(context, deviceName) {
                    navController.navigate(SettingsWearMainView.FAVORITES)
                }
            }
        }
    }
}
