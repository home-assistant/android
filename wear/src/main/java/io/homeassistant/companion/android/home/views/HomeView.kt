package io.homeassistant.companion.android.home.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.tiles.TileService
import io.homeassistant.companion.android.common.sensors.id
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.tiles.ShortcutsTile
import io.homeassistant.companion.android.tiles.TemplateTile
import io.homeassistant.companion.android.views.ChooseEntityView

private const val ARG_SCREEN_SENSOR_MANAGER_ID = "sensorManagerId"
private const val ARG_SCREEN_SHORTCUTS_TILE_ID = "shortcutsTileId"
private const val ARG_SCREEN_SHORTCUTS_TILE_ENTITY_INDEX = "shortcutsTileEntityIndex"

private const val SCREEN_LANDING = "landing"
private const val SCREEN_ENTITY_DETAIL = "entity_detail"
private const val SCREEN_ENTITY_LIST = "entity_list"
private const val SCREEN_MANAGE_SENSORS = "manage_all_sensors"
private const val SCREEN_SINGLE_SENSOR_MANAGER = "sensor_manager"
private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_SET_FAVORITES = "set_favorites"
private const val ROUTE_SHORTCUTS_TILE = "shortcuts_tile"
private const val SCREEN_SELECT_SHORTCUTS_TILE = "select_shortcuts_tile"
private const val SCREEN_SET_SHORTCUTS_TILE = "set_shortcuts_tile"
private const val SCREEN_SHORTCUTS_TILE_CHOOSE_ENTITY = "shortcuts_tile_choose_entity"
private const val SCREEN_SET_TILE_TEMPLATE = "set_tile_template"
private const val SCREEN_SET_TILE_TEMPLATE_REFRESH_INTERVAL = "set_tile_template_refresh_interval"

const val DEEPLINK_SENSOR_MANAGER = "ha_wear://$SCREEN_SINGLE_SENSOR_MANAGER"
const val DEEPLINK_PREFIX_SET_SHORTCUT_TILE = "ha_wear://$SCREEN_SET_SHORTCUTS_TILE"

@Composable
fun LoadHomePage(
    mainViewModel: MainViewModel
) {
    val context = LocalContext.current

    WearAppTheme {
        val swipeDismissableNavController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(
            navController = swipeDismissableNavController,
            startDestination = SCREEN_LANDING
        ) {
            composable(SCREEN_LANDING) {
                MainView(
                    mainViewModel = mainViewModel,
                    favoriteEntityIds = mainViewModel.favoriteEntityIds.value,
                    onEntityClicked = { id, state -> mainViewModel.toggleEntity(id, state) },
                    onEntityLongClicked = { entityId ->
                        swipeDismissableNavController.navigate("$SCREEN_ENTITY_DETAIL/$entityId")
                    },
                    onRetryLoadEntitiesClicked = mainViewModel::loadEntities,
                    onSettingsClicked = { swipeDismissableNavController.navigate(SCREEN_SETTINGS) },
                    onNavigationClicked = { lists, order, filter ->
                        mainViewModel.entityLists.clear()
                        mainViewModel.entityLists.putAll(lists)
                        mainViewModel.entityListsOrder.clear()
                        mainViewModel.entityListsOrder.addAll(order)
                        mainViewModel.entityListFilter = filter
                        swipeDismissableNavController.navigate(SCREEN_ENTITY_LIST)
                    },
                    isHapticEnabled = mainViewModel.isHapticEnabled.value,
                    isToastEnabled = mainViewModel.isToastEnabled.value
                )
            }
            composable("$SCREEN_ENTITY_DETAIL/{entityId}") {
                val entity = mainViewModel.entities[it.arguments?.getString("entityId")]
                if (entity != null) {
                    DetailsPanelView(
                        entity = entity,
                        onEntityToggled = { entityId, state ->
                            mainViewModel.toggleEntity(entityId, state)
                        },
                        onFanSpeedChanged = { speed ->
                            mainViewModel.setFanSpeed(
                                entity.entityId,
                                speed
                            )
                        },
                        onBrightnessChanged = { brightness ->
                            mainViewModel.setBrightness(
                                entity.entityId,
                                brightness
                            )
                        },
                        onColorTempChanged = { colorTemp, isKelvin ->
                            mainViewModel.setColorTemp(
                                entity.entityId,
                                colorTemp,
                                isKelvin
                            )
                        },
                        isToastEnabled = mainViewModel.isToastEnabled.value,
                        isHapticEnabled = mainViewModel.isHapticEnabled.value
                    )
                }
            }
            composable(SCREEN_ENTITY_LIST) {
                EntityViewList(
                    entityLists = mainViewModel.entityLists,
                    entityListsOrder = mainViewModel.entityListsOrder,
                    entityListFilter = mainViewModel.entityListFilter,
                    onEntityClicked = { entityId, state ->
                        mainViewModel.toggleEntity(entityId, state)
                    },
                    onEntityLongClicked = { entityId ->
                        swipeDismissableNavController.navigate("$SCREEN_ENTITY_DETAIL/$entityId")
                    },
                    isHapticEnabled = mainViewModel.isHapticEnabled.value,
                    isToastEnabled = mainViewModel.isToastEnabled.value
                )
            }
            composable(SCREEN_SETTINGS) {
                SettingsView(
                    loadingState = mainViewModel.loadingState.value,
                    favorites = mainViewModel.favoriteEntityIds.value,
                    onClickSetFavorites = {
                        swipeDismissableNavController.navigate(
                            SCREEN_SET_FAVORITES
                        )
                    },
                    onClearFavorites = { mainViewModel.clearFavorites() },
                    onClickSetShortcuts = {
                        mainViewModel.loadShortcutTileEntities()
                        swipeDismissableNavController.navigate(
                            "$ROUTE_SHORTCUTS_TILE/$SCREEN_SELECT_SHORTCUTS_TILE"
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
                    isFavoritesOnly = mainViewModel.isFavoritesOnly,
                    isAssistantAppAllowed = mainViewModel.isAssistantAppAllowed,
                    onHapticEnabled = { mainViewModel.setHapticEnabled(it) },
                    onToastEnabled = { mainViewModel.setToastEnabled(it) },
                    setFavoritesOnly = { mainViewModel.setWearFavoritesOnly(it) },
                    onClickTemplateTile = { swipeDismissableNavController.navigate(SCREEN_SET_TILE_TEMPLATE) },
                    onAssistantAppAllowed = mainViewModel::setAssistantApp
                )
            }
            composable(SCREEN_SET_FAVORITES) {
                SetFavoritesView(
                    mainViewModel,
                    mainViewModel.favoriteEntityIds.value
                ) { entityId, isSelected ->
                    if (isSelected) {
                        mainViewModel.addFavoriteEntity(entityId)
                    } else {
                        mainViewModel.removeFavoriteEntity(entityId)
                    }
                }
            }
            composable("$ROUTE_SHORTCUTS_TILE/$SCREEN_SELECT_SHORTCUTS_TILE") {
                SelectShortcutsTileView(
                    shortcutTileEntitiesCountById = mainViewModel.shortcutEntitiesMap.mapValues { (_, entities) -> entities.size },
                    onSelectShortcutsTile = { tileId ->
                        swipeDismissableNavController.navigate("$ROUTE_SHORTCUTS_TILE/$tileId/$SCREEN_SET_SHORTCUTS_TILE")
                    },
                    isShowShortcutTextEnabled = mainViewModel.isShowShortcutTextEnabled.value,
                    onShowShortcutTextEnabled = {
                        mainViewModel.setShowShortcutTextEnabled(it)
                        ShortcutsTile.requestUpdate(context)
                    }
                )
            }
            composable(
                route = "$ROUTE_SHORTCUTS_TILE/{$ARG_SCREEN_SHORTCUTS_TILE_ID}/$SCREEN_SET_SHORTCUTS_TILE",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_SHORTCUTS_TILE_ID) {
                        type = NavType.StringType
                    }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "$DEEPLINK_PREFIX_SET_SHORTCUT_TILE/{$ARG_SCREEN_SHORTCUTS_TILE_ID}" }
                )
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments!!.getString(ARG_SCREEN_SHORTCUTS_TILE_ID)!!.toIntOrNull()
                SetShortcutsTileView(
                    shortcutEntities = mainViewModel.shortcutEntitiesMap[tileId] ?: emptyList(),
                    onShortcutEntitySelectionChange = { entityIndex ->
                        swipeDismissableNavController.navigate("$ROUTE_SHORTCUTS_TILE/$tileId/$SCREEN_SHORTCUTS_TILE_CHOOSE_ENTITY/$entityIndex")
                    }
                )
            }
            composable(
                route = "$ROUTE_SHORTCUTS_TILE/{$ARG_SCREEN_SHORTCUTS_TILE_ID}/$SCREEN_SHORTCUTS_TILE_CHOOSE_ENTITY/{$ARG_SCREEN_SHORTCUTS_TILE_ENTITY_INDEX}",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_SHORTCUTS_TILE_ID) {
                        type = NavType.StringType
                    },
                    navArgument(name = ARG_SCREEN_SHORTCUTS_TILE_ENTITY_INDEX) {
                        type = NavType.IntType
                    }
                )
            ) { backStackEntry ->
                val entityIndex = backStackEntry.arguments!!.getInt(ARG_SCREEN_SHORTCUTS_TILE_ENTITY_INDEX)
                val tileId = backStackEntry.arguments!!.getString(ARG_SCREEN_SHORTCUTS_TILE_ID)!!.toIntOrNull()
                ChooseEntityView(
                    entitiesByDomainOrder = mainViewModel.entitiesByDomainOrder,
                    entitiesByDomain = mainViewModel.entitiesByDomain,
                    favoriteEntityIds = mainViewModel.favoriteEntityIds,
                    onNoneClicked = {
                        mainViewModel.clearTileShortcut(tileId, entityIndex)
                        ShortcutsTile.requestUpdate(context)
                        swipeDismissableNavController.navigateUp()
                    },
                    onEntitySelected = { entity ->
                        mainViewModel.setTileShortcut(tileId, entityIndex, entity)
                        ShortcutsTile.requestUpdate(context)
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
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "$DEEPLINK_SENSOR_MANAGER/{$ARG_SCREEN_SENSOR_MANAGER_ID}" }
                )
            ) { backStackEntry ->
                val sensorManagerId =
                    backStackEntry.arguments?.getString(ARG_SCREEN_SENSOR_MANAGER_ID)
                val sensorManager = getSensorManagers().first { sensorManager ->
                    sensorManager.id() == sensorManagerId
                }
                mainViewModel.updateAllSensors(sensorManager)
                SensorManagerUi(
                    allSensors = mainViewModel.sensors.value,
                    allAvailSensors = mainViewModel.availableSensors,
                    sensorManager = sensorManager
                ) { sensorId, isEnabled ->
                    mainViewModel.enableDisableSensor(sensorManager, sensorId, isEnabled)
                }
            }
        }
    }
}
