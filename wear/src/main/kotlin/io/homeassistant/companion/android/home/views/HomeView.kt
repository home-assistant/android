package io.homeassistant.companion.android.home.views

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.tiles.TileService
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.sensors.id
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.tiles.CameraTile
import io.homeassistant.companion.android.tiles.ShortcutsTile
import io.homeassistant.companion.android.tiles.TemplateTile
import io.homeassistant.companion.android.tiles.ThermostatTile
import io.homeassistant.companion.android.views.ChooseEntityView

private const val ARG_SCREEN_SENSOR_MANAGER_ID = "sensorManagerId"
private const val ARG_SCREEN_CAMERA_TILE_ID = "cameraTileId"
private const val ARG_SCREEN_THERMOSTAT_TILE_ID = "thermostatTileId"
private const val ARG_SCREEN_SHORTCUTS_TILE_ID = "shortcutsTileId"
private const val ARG_SCREEN_SHORTCUTS_TILE_ENTITY_INDEX = "shortcutsTileEntityIndex"
private const val ARG_SCREEN_TEMPLATE_TILE_ID = "templateTileId"

private const val SCREEN_LANDING = "landing"
private const val SCREEN_ENTITY_DETAIL = "entity_detail"
private const val SCREEN_ENTITY_LIST = "entity_list"
private const val SCREEN_MANAGE_SENSORS = "manage_all_sensors"
private const val SCREEN_SINGLE_SENSOR_MANAGER = "sensor_manager"
private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_SET_FAVORITES = "set_favorites"
private const val ROUTE_CAMERA_TILE = "camera_tile"
private const val SCREEN_SELECT_CAMERA_TILE = "select_camera_tile"
private const val SCREEN_SET_CAMERA_TILE = "set_camera_tile"
private const val SCREEN_SET_CAMERA_TILE_ENTITY = "entity"
private const val SCREEN_SET_CAMERA_TILE_REFRESH_INTERVAL = "refresh_interval"
private const val ROUTE_THERMOSTAT_TILE = "thermostat_tile"
private const val SCREEN_SELECT_THERMOSTAT_TILE = "select_thermostat_tile"
private const val SCREEN_SET_THERMOSTAT_TILE = "set_thermostat_tile"
private const val SCREEN_SET_THERMOSTAT_TILE_ENTITY = "entity"
private const val SCREEN_SET_THERMOSTAT_TILE_REFRESH_INTERVAL = "refresh_interval"
private const val ROUTE_SHORTCUTS_TILE = "shortcuts_tile"
private const val ROUTE_TEMPLATE_TILE = "template_tile"
private const val SCREEN_SELECT_SHORTCUTS_TILE = "select_shortcuts_tile"
private const val SCREEN_SELECT_TEMPLATE_TILE = "select_template_tile"
private const val SCREEN_SET_SHORTCUTS_TILE = "set_shortcuts_tile"
private const val SCREEN_SHORTCUTS_TILE_CHOOSE_ENTITY = "shortcuts_tile_choose_entity"
private const val SCREEN_SET_TILE_TEMPLATE = "set_tile_template"
private const val SCREEN_SET_TILE_TEMPLATE_REFRESH_INTERVAL = "set_tile_template_refresh_interval"

const val DEEPLINK_SENSOR_MANAGER = "ha_wear://$SCREEN_SINGLE_SENSOR_MANAGER"
const val DEEPLINK_PREFIX_SET_CAMERA_TILE = "ha_wear://$SCREEN_SET_CAMERA_TILE"
const val DEEPLINK_PREFIX_SET_THERMOSTAT_TILE = "ha_wear://$SCREEN_SET_THERMOSTAT_TILE"
const val DEEPLINK_PREFIX_SET_SHORTCUT_TILE = "ha_wear://$SCREEN_SET_SHORTCUTS_TILE"
const val DEEPLINK_PREFIX_SET_TEMPLATE_TILE = "ha_wear://$SCREEN_SET_TILE_TEMPLATE"

@Composable
fun LoadHomePage(mainViewModel: MainViewModel) {
    val context = LocalContext.current

    WearAppTheme {
        val swipeDismissableNavController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(
            navController = swipeDismissableNavController,
            startDestination = SCREEN_LANDING,
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
                    isToastEnabled = mainViewModel.isToastEnabled.value,
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
                                speed,
                            )
                        },
                        onBrightnessChanged = { brightness ->
                            mainViewModel.setBrightness(
                                entity.entityId,
                                brightness,
                            )
                        },
                        onColorTempChanged = { colorTemp, isKelvin ->
                            mainViewModel.setColorTemp(
                                entity.entityId,
                                colorTemp,
                                isKelvin,
                            )
                        },
                        isToastEnabled = mainViewModel.isToastEnabled.value,
                        isHapticEnabled = mainViewModel.isHapticEnabled.value,
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
                    isToastEnabled = mainViewModel.isToastEnabled.value,
                )
            }
            composable(SCREEN_SETTINGS) {
                val notificationLaunch =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        mainViewModel.refreshNotificationPermission()
                    }
                SettingsView(
                    loadingState = mainViewModel.loadingState.value,
                    favorites = mainViewModel.favoriteEntityIds.value,
                    onClickSetFavorites = {
                        swipeDismissableNavController.navigate(
                            SCREEN_SET_FAVORITES,
                        )
                    },
                    onClearFavorites = { mainViewModel.clearFavorites() },
                    onClickSetShortcuts = {
                        mainViewModel.loadShortcutTileEntities()
                        swipeDismissableNavController.navigate(
                            "$ROUTE_SHORTCUTS_TILE/$SCREEN_SELECT_SHORTCUTS_TILE",
                        )
                    },
                    onClickSensors = {
                        swipeDismissableNavController.navigate(
                            SCREEN_MANAGE_SENSORS,
                        )
                    },
                    onClickLogout = { mainViewModel.logout() },
                    isHapticEnabled = mainViewModel.isHapticEnabled.value,
                    isToastEnabled = mainViewModel.isToastEnabled.value,
                    isFavoritesOnly = mainViewModel.isFavoritesOnly,
                    isAssistantAppAllowed = mainViewModel.isAssistantAppAllowed,
                    areNotificationsAllowed = mainViewModel.areNotificationsAllowed,
                    onHapticEnabled = { mainViewModel.setHapticEnabled(it) },
                    onToastEnabled = { mainViewModel.setToastEnabled(it) },
                    setFavoritesOnly = { mainViewModel.setWearFavoritesOnly(it) },
                    onClickCameraTile = {
                        swipeDismissableNavController.navigate("$ROUTE_CAMERA_TILE/$SCREEN_SELECT_CAMERA_TILE")
                    },
                    onClickTemplateTiles = {
                        mainViewModel.loadTemplateTiles()
                        swipeDismissableNavController.navigate("$ROUTE_TEMPLATE_TILE/$SCREEN_SELECT_TEMPLATE_TILE")
                    },
                    onClickThermostatTiles = {
                        swipeDismissableNavController.navigate("$ROUTE_THERMOSTAT_TILE/$SCREEN_SELECT_THERMOSTAT_TILE")
                    },
                    onAssistantAppAllowed = mainViewModel::setAssistantApp,
                    onClickNotifications = {
                        notificationLaunch.launch(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            },
                        )
                    },
                )
            }
            composable(SCREEN_SET_FAVORITES) {
                SetFavoritesView(
                    mainViewModel,
                    mainViewModel.favoriteEntityIds.value,
                ) { entityId, isSelected ->
                    if (isSelected) {
                        mainViewModel.addFavoriteEntity(entityId)
                    } else {
                        mainViewModel.removeFavoriteEntity(entityId)
                    }
                }
            }
            composable("$ROUTE_CAMERA_TILE/$SCREEN_SELECT_CAMERA_TILE") {
                SelectCameraTileView(
                    tiles = mainViewModel.cameraTiles.value,
                    onSelectTile = { tileId ->
                        swipeDismissableNavController.navigate("$ROUTE_CAMERA_TILE/$tileId/$SCREEN_SET_CAMERA_TILE")
                    },
                )
            }
            composable(
                route = "$ROUTE_CAMERA_TILE/{$ARG_SCREEN_CAMERA_TILE_ID}/$SCREEN_SET_CAMERA_TILE",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_CAMERA_TILE_ID) {
                        type = NavType.IntType
                    },
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "$DEEPLINK_PREFIX_SET_CAMERA_TILE/{$ARG_SCREEN_CAMERA_TILE_ID}" },
                ),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments?.getInt(ARG_SCREEN_CAMERA_TILE_ID)
                SetCameraTileView(
                    tile = mainViewModel.cameraTiles.value.firstOrNull { it.id == tileId },
                    entities = mainViewModel.cameraEntitiesMap[CAMERA_DOMAIN],
                    onSelectEntity = {
                        swipeDismissableNavController.navigate(
                            "$ROUTE_CAMERA_TILE/$tileId/$SCREEN_SET_CAMERA_TILE_ENTITY",
                        )
                    },
                    onSelectRefreshInterval = {
                        swipeDismissableNavController.navigate(
                            "$ROUTE_CAMERA_TILE/$tileId/$SCREEN_SET_CAMERA_TILE_REFRESH_INTERVAL",
                        )
                    },
                )
            }
            composable(
                route = "$ROUTE_CAMERA_TILE/{$ARG_SCREEN_CAMERA_TILE_ID}/$SCREEN_SET_CAMERA_TILE_ENTITY",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_CAMERA_TILE_ID) {
                        type = NavType.IntType
                    },
                ),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments?.getInt(ARG_SCREEN_CAMERA_TILE_ID)
                val cameraDomains = remember { mutableStateListOf(CAMERA_DOMAIN) }
                val cameraFavorites = remember { mutableStateOf(emptyList<String>()) } // There are no camera favorites
                ChooseEntityView(
                    entitiesByDomainOrder = cameraDomains,
                    entitiesByDomain = mainViewModel.cameraEntitiesMap,
                    favoriteEntityIds = cameraFavorites,
                    onNoneClicked = {},
                    onEntitySelected = { entity ->
                        tileId?.let {
                            mainViewModel.setCameraTileEntity(it, entity.entityId)
                            TileService.getUpdater(context).requestUpdate(CameraTile::class.java)
                        }
                        swipeDismissableNavController.navigateUp()
                    },
                    allowNone = false,
                )
            }
            composable(
                route = "$ROUTE_CAMERA_TILE/{$ARG_SCREEN_CAMERA_TILE_ID}/$SCREEN_SET_CAMERA_TILE_REFRESH_INTERVAL",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_CAMERA_TILE_ID) {
                        type = NavType.IntType
                    },
                ),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments?.getInt(ARG_SCREEN_CAMERA_TILE_ID)
                RefreshIntervalPickerView(
                    currentInterval = (
                        mainViewModel.cameraTiles.value
                            .firstOrNull { it.id == tileId }?.refreshInterval
                            ?: CameraTile.DEFAULT_REFRESH_INTERVAL
                        ).toInt(),
                ) { interval ->
                    tileId?.let {
                        mainViewModel.setCameraTileRefreshInterval(it, interval.toLong())
                    }
                    swipeDismissableNavController.navigateUp()
                }
            }
            composable("$ROUTE_THERMOSTAT_TILE/$SCREEN_SELECT_THERMOSTAT_TILE") {
                SelectThermostatTileView(
                    tiles = mainViewModel.thermostatTiles.value,
                    onSelectTile = { tileId ->
                        swipeDismissableNavController.navigate(
                            "$ROUTE_THERMOSTAT_TILE/$tileId/$SCREEN_SET_THERMOSTAT_TILE",
                        )
                    },
                )
            }
            composable(
                route = "$ROUTE_THERMOSTAT_TILE/{$ARG_SCREEN_THERMOSTAT_TILE_ID}/$SCREEN_SET_THERMOSTAT_TILE",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_THERMOSTAT_TILE_ID) {
                        type = NavType.IntType
                    },
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = "$DEEPLINK_PREFIX_SET_THERMOSTAT_TILE/{$ARG_SCREEN_THERMOSTAT_TILE_ID}"
                    },
                ),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments?.getInt(ARG_SCREEN_THERMOSTAT_TILE_ID)
                SetThermostatTileView(
                    tile = mainViewModel.thermostatTiles.value.firstOrNull { it.id == tileId },
                    entities = mainViewModel.climateEntitiesMap["climate"],
                    onSelectEntity = {
                        swipeDismissableNavController.navigate(
                            "$ROUTE_THERMOSTAT_TILE/$tileId/$SCREEN_SET_THERMOSTAT_TILE_ENTITY",
                        )
                    },
                    onSelectRefreshInterval = {
                        swipeDismissableNavController.navigate(
                            "$ROUTE_THERMOSTAT_TILE/$tileId/$SCREEN_SET_CAMERA_TILE_REFRESH_INTERVAL",
                        )
                    },
                    onNameEnabled = { tileIdToggle, showName ->
                        mainViewModel.setThermostatTileShowName(tileIdToggle, showName)
                    },
                )
            }
            composable(
                route = "$ROUTE_THERMOSTAT_TILE/{$ARG_SCREEN_THERMOSTAT_TILE_ID}/$SCREEN_SET_THERMOSTAT_TILE_ENTITY",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_THERMOSTAT_TILE_ID) {
                        type = NavType.IntType
                    },
                ),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments?.getInt(ARG_SCREEN_THERMOSTAT_TILE_ID)
                val climateDomains = remember { mutableStateListOf("climate") }
                // There are no climate favorites
                val climateFavorites = remember { mutableStateOf(emptyList<String>()) }
                ChooseEntityView(
                    entitiesByDomainOrder = climateDomains,
                    entitiesByDomain = mainViewModel.climateEntitiesMap,
                    favoriteEntityIds = climateFavorites,
                    onNoneClicked = {},
                    onEntitySelected = { entity ->
                        tileId?.let {
                            mainViewModel.setThermostatTileEntity(it, entity.entityId)
                            TileService.getUpdater(context).requestUpdate(ThermostatTile::class.java)
                        }
                        swipeDismissableNavController.navigateUp()
                    },
                    allowNone = false,
                )
            }
            composable(
                route = "$ROUTE_THERMOSTAT_TILE/{$ARG_SCREEN_THERMOSTAT_TILE_ID}/" +
                    SCREEN_SET_THERMOSTAT_TILE_REFRESH_INTERVAL,
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_THERMOSTAT_TILE_ID) {
                        type = NavType.IntType
                    },
                ),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments?.getInt(ARG_SCREEN_THERMOSTAT_TILE_ID)
                RefreshIntervalPickerView(
                    currentInterval = (
                        mainViewModel.thermostatTiles.value
                            .firstOrNull { it.id == tileId }?.refreshInterval
                            ?: ThermostatTile.DEFAULT_REFRESH_INTERVAL
                        ).toInt(),
                ) { interval ->
                    tileId?.let {
                        mainViewModel.setThermostatTileRefreshInterval(it, interval.toLong())
                    }
                    swipeDismissableNavController.navigateUp()
                }
            }
            composable("$ROUTE_SHORTCUTS_TILE/$SCREEN_SELECT_SHORTCUTS_TILE") {
                SelectShortcutsTileView(
                    shortcutTileEntitiesCountById = mainViewModel.shortcutEntitiesMap.mapValues { (_, entities) ->
                        entities.size
                    },
                    onSelectShortcutsTile = { tileId ->
                        swipeDismissableNavController.navigate(
                            "$ROUTE_SHORTCUTS_TILE/$tileId/$SCREEN_SET_SHORTCUTS_TILE",
                        )
                    },
                    isShowShortcutTextEnabled = mainViewModel.isShowShortcutTextEnabled.value,
                    onShowShortcutTextEnabled = {
                        mainViewModel.setShowShortcutTextEnabled(it)
                        ShortcutsTile.requestUpdate(context)
                    },
                )
            }
            composable(
                route = "$ROUTE_SHORTCUTS_TILE/{$ARG_SCREEN_SHORTCUTS_TILE_ID}/$SCREEN_SET_SHORTCUTS_TILE",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_SHORTCUTS_TILE_ID) {
                        type = NavType.StringType
                    },
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "$DEEPLINK_PREFIX_SET_SHORTCUT_TILE/{$ARG_SCREEN_SHORTCUTS_TILE_ID}" },
                ),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments!!.getString(ARG_SCREEN_SHORTCUTS_TILE_ID)!!.toIntOrNull()
                SetShortcutsTileView(
                    shortcutEntities = mainViewModel.shortcutEntitiesMap[tileId] ?: emptyList(),
                    onShortcutEntitySelectionChange = { entityIndex ->
                        swipeDismissableNavController.navigate(
                            "$ROUTE_SHORTCUTS_TILE/$tileId/$SCREEN_SHORTCUTS_TILE_CHOOSE_ENTITY/$entityIndex",
                        )
                    },
                )
            }
            composable(
                route = "$ROUTE_SHORTCUTS_TILE/{$ARG_SCREEN_SHORTCUTS_TILE_ID}/$SCREEN_SHORTCUTS_TILE_CHOOSE_ENTITY/" +
                    "{$ARG_SCREEN_SHORTCUTS_TILE_ENTITY_INDEX}",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_SHORTCUTS_TILE_ID) {
                        type = NavType.StringType
                    },
                    navArgument(name = ARG_SCREEN_SHORTCUTS_TILE_ENTITY_INDEX) {
                        type = NavType.IntType
                    },
                ),
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
                    },
                )
            }
            composable("$ROUTE_TEMPLATE_TILE/$SCREEN_SELECT_TEMPLATE_TILE") {
                SelectTemplateTileView(
                    templateTiles = mainViewModel.templateTiles,
                    onSelectTemplateTile = { tileId ->
                        swipeDismissableNavController.navigate("$ROUTE_TEMPLATE_TILE/$tileId/$SCREEN_SET_TILE_TEMPLATE")
                    },
                )
            }
            composable(
                route = "$ROUTE_TEMPLATE_TILE/{$ARG_SCREEN_TEMPLATE_TILE_ID}/$SCREEN_SET_TILE_TEMPLATE",
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_TEMPLATE_TILE_ID) {
                        type = NavType.StringType
                    },
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "$DEEPLINK_PREFIX_SET_TEMPLATE_TILE/{$ARG_SCREEN_TEMPLATE_TILE_ID}" },
                ),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments!!.getString(ARG_SCREEN_TEMPLATE_TILE_ID)!!.toIntOrNull()

                TemplateTileSettingsView(
                    templateContent = mainViewModel.templateTiles[tileId]?.template ?: "",
                    refreshInterval = mainViewModel.templateTiles[tileId]?.refreshInterval ?: 0,
                ) {
                    swipeDismissableNavController.navigate(
                        "$ROUTE_TEMPLATE_TILE/$tileId/$SCREEN_SET_TILE_TEMPLATE_REFRESH_INTERVAL",
                    )
                }
            }
            composable(
                route = "$ROUTE_TEMPLATE_TILE/{$ARG_SCREEN_TEMPLATE_TILE_ID}/" +
                    SCREEN_SET_TILE_TEMPLATE_REFRESH_INTERVAL,
                arguments = listOf(
                    navArgument(name = ARG_SCREEN_TEMPLATE_TILE_ID) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val tileId = backStackEntry.arguments!!.getString(ARG_SCREEN_TEMPLATE_TILE_ID)!!.toInt()
                RefreshIntervalPickerView(
                    currentInterval = mainViewModel.templateTiles[tileId]?.refreshInterval ?: 0,
                ) {
                    mainViewModel.setTemplateTileRefreshInterval(tileId, it)
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
                    },
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "$DEEPLINK_SENSOR_MANAGER/{$ARG_SCREEN_SENSOR_MANAGER_ID}" },
                ),
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
                    sensorManager = sensorManager,
                ) { sensorId, isEnabled ->
                    mainViewModel.enableDisableSensor(sensorManager, sensorId, isEnabled)
                }
            }
        }
    }
}
