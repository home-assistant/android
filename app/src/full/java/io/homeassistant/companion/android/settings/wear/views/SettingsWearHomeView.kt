package io.homeassistant.companion.android.settings.wear.views

import android.content.Intent
import android.net.Uri
import androidx.compose.material.IconButton
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.themeadapter.material.MdcTheme
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.settings.wear.SettingsWearViewModel
import io.homeassistant.companion.android.common.R as commonR

const val WEAR_DOCS_LINK = "https://companion.home-assistant.io/docs/wear-os/"

@Composable
fun LoadSettingsHomeView(
    settingsWearViewModel: SettingsWearViewModel,
    deviceName: String,
    loginWearOs: () -> Unit,
    onStartBackClicked: () -> Unit
) {
    MdcTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = SettingsWearMainView.LANDING) {
            composable(SettingsWearMainView.FAVORITES) {
                LoadWearFavoritesSettings(
                    settingsWearViewModel = settingsWearViewModel,
                    onBackClicked = { navController.navigateUp() },
                    events = settingsWearViewModel.resultSnackbar
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
                    navigateTemplateTile = { navController.navigate(SettingsWearMainView.TEMPLATE) },
                    loginWearOs = loginWearOs,
                    onBackClicked = onStartBackClicked,
                    events = settingsWearViewModel.resultSnackbar
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

@Composable
fun SettingsWearTopAppBar(
    title: @Composable () -> Unit,
    onBackClicked: () -> Unit,
    docsLink: String? = null
) {
    val context = LocalContext.current
    TopAppBar(
        title = title,
        navigationIcon = {
            IconButton(onClick = onBackClicked) {
                Image(
                    asset = CommunityMaterial.Icon.cmd_arrow_left,
                    colorFilter = ColorFilter.tint(colorResource(commonR.color.colorOnBackground))
                )
            }
        },
        actions = {
            if (!docsLink.isNullOrBlank()) {
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(docsLink))
                    context.startActivity(intent)
                }) {
                    Image(
                        asset = CommunityMaterial.Icon2.cmd_help_circle_outline,
                        contentDescription = stringResource(commonR.string.help),
                        colorFilter = ColorFilter.tint(colorResource(commonR.color.colorOnBackground))
                    )
                }
            }
        },
        backgroundColor = colorResource(id = commonR.color.colorBackground),
        contentColor = colorResource(id = commonR.color.colorOnBackground)
    )
}
