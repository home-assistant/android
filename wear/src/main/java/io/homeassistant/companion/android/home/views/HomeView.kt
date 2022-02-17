package io.homeassistant.companion.android.home.views

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.tiles.TileService
import io.homeassistant.companion.android.common.sensors.id
import io.homeassistant.companion.android.database.wear.Favorites
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.tiles.ShortcutsTile
import io.homeassistant.companion.android.tiles.TemplateTile
import io.homeassistant.companion.android.common.R as commonR

private const val ARG_SCREEN_SENSOR_MANAGER_ID = "sensorManagerId"

private const val SCREEN_LANDING = "landing"
private const val SCREEN_ENTITY_LIST = "entity_list"
private const val SCREEN_MANAGE_SENSORS = "manage_all_sensors"
private const val SCREEN_SINGLE_SENSOR_MANAGER = "sensor_manager"
private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_SET_FAVORITES = "set_favorites"
private const val SCREEN_SET_TILE_SHORTCUTS = "set_tile_shortcuts"
private const val SCREEN_SELECT_TILE_SHORTCUT = "select_tile_shortcut"
private const val SCREEN_SET_TILE_TEMPLATE = "set_tile_template"
private const val SCREEN_SET_TILE_TEMPLATE_REFRESH_INTERVAL = "set_tile_template_refresh_interval"

@ExperimentalComposeUiApi
@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Composable
fun LoadHomePage(
    mainViewModel: MainViewModel
) {
    var shortcutEntitySelectionIndex: Int by remember { mutableStateOf(0) }
    val context = LocalContext.current

    WearAppTheme {
        if (mainViewModel.entities.isNullOrEmpty() && mainViewModel.favoriteEntityIds.isNullOrEmpty()) {
            Column {
                ListHeader(id = commonR.string.loading)
                Chip(
                    modifier = Modifier
                        .padding(top = 30.dp, start = 10.dp, end = 10.dp),
                    label = {
                        Text(
                            text = stringResource(commonR.string.loading_entities),
                            textAlign = TextAlign.Center
                        )
                    },
                    onClick = { /* No op */ },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        } else {
            val swipeDismissableNavController = rememberSwipeDismissableNavController()
            SwipeDismissableNavHost(
                navController = swipeDismissableNavController,
                startDestination = SCREEN_LANDING
            ) {
                composable(SCREEN_LANDING) {
                    MainView(
                        mainViewModel,
                        mainViewModel.favoriteEntityIds,
                        { id, state -> mainViewModel.toggleEntity(id, state) },
                        { swipeDismissableNavController.navigate(SCREEN_SETTINGS) },
                        { lists, order, filter ->
                            mainViewModel.entityLists.clear()
                            mainViewModel.entityLists.putAll(lists)
                            mainViewModel.entityListsOrder.clear()
                            mainViewModel.entityListsOrder.addAll(order)
                            mainViewModel.entityListFilter = filter
                            swipeDismissableNavController.navigate(SCREEN_ENTITY_LIST)
                        },
                        mainViewModel.isHapticEnabled.value,
                        mainViewModel.isToastEnabled.value,
                        { id -> mainViewModel.removeFavorites(id) }
                    )
                }
                composable(SCREEN_ENTITY_LIST) {
                    EntityViewList(
                        entityLists = mainViewModel.entityLists,
                        entityListsOrder = mainViewModel.entityListsOrder,
                        entityListFilter = mainViewModel.entityListFilter,
                        onEntityClicked =
                        { entityId, state ->
                            mainViewModel.toggleEntity(entityId, state)
                        },
                        isHapticEnabled = mainViewModel.isHapticEnabled.value,
                        isToastEnabled = mainViewModel.isToastEnabled.value
                    )
                }
                composable(SCREEN_SETTINGS) {
                    SettingsView(
                        favorites = mainViewModel.favoriteEntityIds,
                        onClickSetFavorites = {
                            swipeDismissableNavController.navigate(
                                SCREEN_SET_FAVORITES
                            )
                        },
                        onClearFavorites = { mainViewModel.clearFavorites() },
                        onClickSetShortcuts = {
                            swipeDismissableNavController.navigate(
                                SCREEN_SET_TILE_SHORTCUTS
                            )
                        },
                        onClickSensors = {
                            swipeDismissableNavController.navigate(
                                SCREEN_MANAGE_SENSORS
                            )
                        },
                        onClickLogout = { mainViewModel.logout() },
                        isHapticEnabled = mainViewModel.isHapticEnabled.value,
                        isToastEnabled = mainViewModel.isToastEnabled.value,
                        onHapticEnabled = { mainViewModel.setHapticEnabled(it) },
                        onToastEnabled = { mainViewModel.setToastEnabled(it) }
                    ) { swipeDismissableNavController.navigate(SCREEN_SET_TILE_TEMPLATE) }
                }
                composable(SCREEN_SET_FAVORITES) {
                    SetFavoritesView(
                        mainViewModel,
                        mainViewModel.favoriteEntityIds
                    ) { entityId, position, isSelected ->
                        val favorites = Favorites(entityId, position)
                        if (isSelected) {
                            mainViewModel.addFavorites(favorites)
                        } else {
                            mainViewModel.removeFavorites(entityId)
                        }
                    }
                }
                composable(SCREEN_SET_TILE_SHORTCUTS) {
                    SetTileShortcutsView(
                        shortcutEntities = mainViewModel.shortcutEntities,
                        onShortcutEntitySelectionChange = {
                            shortcutEntitySelectionIndex = it
                            swipeDismissableNavController.navigate(SCREEN_SELECT_TILE_SHORTCUT)
                        },
                        isShowShortcutTextEnabled = mainViewModel.isShowShortcutTextEnabled.value,
                        onShowShortcutTextEnabled = {
                            mainViewModel.setShowShortcutTextEnabled(it)
                            TileService.getUpdater(context).requestUpdate(ShortcutsTile::class.java)
                        }
                    )
                }
                composable(SCREEN_SELECT_TILE_SHORTCUT) {
                    ChooseEntityView(
                        mainViewModel,
                        {
                            mainViewModel.clearTileShortcut(shortcutEntitySelectionIndex)
                            TileService.getUpdater(context).requestUpdate(ShortcutsTile::class.java)
                            swipeDismissableNavController.navigateUp()
                        },
                        { entity ->
                            mainViewModel.setTileShortcut(shortcutEntitySelectionIndex, entity)
                            TileService.getUpdater(context).requestUpdate(ShortcutsTile::class.java)
                            swipeDismissableNavController.navigateUp()
                        }
                    )
                }
                composable(SCREEN_SET_TILE_TEMPLATE) {
                    TemplateTileSettingsView(
                        templateContent = mainViewModel.templateTileContent.value,
                        refreshInterval = mainViewModel.templateTileRefreshInterval.value
                    ) {
                        swipeDismissableNavController.navigate(
                            SCREEN_SET_TILE_TEMPLATE_REFRESH_INTERVAL
                        )
                    }
                }
                composable(SCREEN_SET_TILE_TEMPLATE_REFRESH_INTERVAL) {
                    RefreshIntervalPickerView(
                        currentInterval = mainViewModel.templateTileRefreshInterval.value
                    ) {
                        mainViewModel.setTemplateTileRefreshInterval(it)
                        TileService.getUpdater(context).requestUpdate(TemplateTile::class.java)
                        swipeDismissableNavController.navigateUp()
                    }
                }
                composable(route = SCREEN_MANAGE_SENSORS) {
                    SensorsView(onClickSensorManager = {
                        swipeDismissableNavController.navigate("$SCREEN_SINGLE_SENSOR_MANAGER/${it.id()}")
                    })
                }
                composable(
                    route = "$SCREEN_SINGLE_SENSOR_MANAGER/{$ARG_SCREEN_SENSOR_MANAGER_ID}",
                    arguments = listOf(
                        navArgument(name = ARG_SCREEN_SENSOR_MANAGER_ID) {
                            type = NavType.StringType
                        }
                    )
                ) { backStackEntry ->
                    val sensorManagerId =
                        backStackEntry.arguments?.getString(ARG_SCREEN_SENSOR_MANAGER_ID)
                    val sensorManager = getSensorManagers().first { sensorManager ->
                        sensorManager.id() == sensorManagerId
                    }
                    SensorManagerUi(
                        allSensors = mainViewModel.sensors,
                        sensorManager = sensorManager,
                    ) { sensorId, isEnabled ->
                        mainViewModel.enableDisableSensor(sensorManager, sensorId, isEnabled)
                    }
                }
            }
        }
    }
}
