package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Alphabetical
import io.github.timoptr.mdiicons.generated.AlphabeticalOff
import io.github.timoptr.mdiicons.generated.Thermostat
import io.github.timoptr.mdiicons.generated.TimerCog
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.theme.getSwitchButtonColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.tiles.ThermostatTile.Companion.DEFAULT_REFRESH_INTERVAL
import io.homeassistant.companion.android.util.intervalToString
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SetThermostatTileView(
    tile: ThermostatTile?,
    entityItem: EntityDisplay?,
    onSelectEntity: () -> Unit,
    onSelectRefreshInterval: () -> Unit,
    onNameEnabled: (Int, Boolean) -> Unit,
    onPreferencesChanged: (Int, (ThermostatTile) -> ThermostatTile) -> Unit,
) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(commonR.string.thermostat_tile)
            }
            item {
                val icon = entityItem?.icon ?: Mdi.Thermostat
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Image(
                            imageVector = icon.rememberImageVector(),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getFilledTonalButtonColors(),
                    label = {
                        Text(
                            text = stringResource(id = R.string.choose_entity),
                        )
                    },
                    secondaryLabel = {
                        Text(entityItem?.name ?: tile?.entityId ?: "")
                    },
                    onClick = onSelectEntity,
                )
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Image(
                            imageVector = Mdi.TimerCog.rememberImageVector(),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getFilledTonalButtonColors(),
                    label = {
                        Text(
                            text = stringResource(id = R.string.refresh_interval),
                        )
                    },
                    secondaryLabel = {
                        Text(
                            intervalToString(
                                LocalContext.current,
                                (tile?.refreshInterval ?: DEFAULT_REFRESH_INTERVAL).toInt(),
                            ),
                        )
                    },
                    onClick = onSelectRefreshInterval,
                )
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    colors = getFilledTonalButtonColors(),
                    label = { Text(stringResource(commonR.string.setting_thermostat_tile_appearance)) },
                    secondaryLabel = {
                        Text(
                            if (tile?.layoutStyle == "enhanced") {
                                stringResource(commonR.string.setting_thermostat_enhanced)
                            } else {
                                stringResource(commonR.string.setting_thermostat_classic)
                            },
                        )
                    },
                    onClick = {
                        tile?.let {
                            onPreferencesChanged(it.id) { current ->
                                current.copy(
                                    layoutStyle = if (current.layoutStyle ==
                                        "enhanced"
                                    ) {
                                        "classic"
                                    } else {
                                        "enhanced"
                                    },
                                )
                            }
                        }
                    },
                )
            }
            if (tile != null && tile.layoutStyle == "classic") {
                item {
                    SwitchButton(
                        modifier = Modifier.fillMaxWidth(),
                        checked = tile.showPowerButton,
                        onCheckedChange = { enabled ->
                            onPreferencesChanged(tile.id) { it.copy(showPowerButton = enabled) }
                        },
                        label = { Text(stringResource(commonR.string.setting_thermostat_power_button_on_tile)) },
                        colors = getSwitchButtonColors(),
                    )
                }
            }
            if (tile?.layoutStyle == "enhanced") {
                item {
                    SwitchButton(
                        modifier = Modifier.fillMaxWidth(),
                        checked = tile?.showHvacModes == true,
                        onCheckedChange = { enabled ->
                            tile?.let {
                                onPreferencesChanged(it.id) { current -> current.copy(showHvacModes = enabled) }
                            }
                        },
                        label = { Text(stringResource(commonR.string.setting_thermostat_hvac_modes)) },
                        colors = getSwitchButtonColors(),
                    )
                }
                item {
                    SwitchButton(
                        modifier = Modifier.fillMaxWidth(),
                        checked = tile?.showFanControl == true,
                        onCheckedChange = { enabled ->
                            tile?.let {
                                onPreferencesChanged(it.id) { current -> current.copy(showFanControl = enabled) }
                            }
                        },
                        label = { Text(stringResource(commonR.string.setting_thermostat_fan_control)) },
                        colors = getSwitchButtonColors(),
                    )
                }
                item {
                    SwitchButton(
                        modifier = Modifier.fillMaxWidth(),
                        checked = tile?.showActivity == true,
                        onCheckedChange = { enabled ->
                            tile?.let {
                                onPreferencesChanged(it.id) { current -> current.copy(showActivity = enabled) }
                            }
                        },
                        label = { Text(stringResource(commonR.string.setting_thermostat_activity)) },
                        colors = getSwitchButtonColors(),
                    )
                }
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = getFilledTonalButtonColors(),
                        label = { Text(stringResource(commonR.string.setting_thermostat_secondary_step)) },
                        secondaryLabel = {
                            Text(
                                stringResource(
                                    commonR.string.setting_thermostat_secondary_step_summary,
                                    tile?.quickAdjustOne ?: 3,
                                ),
                            )
                        },
                        onClick = {
                            tile?.let {
                                onPreferencesChanged(it.id) { current ->
                                    val choices = listOf(3, 5, 2, 10)
                                    val next = choices[(choices.indexOf(current.quickAdjustOne) + 1) % choices.size]
                                    current.copy(quickAdjustOne = next)
                                }
                            }
                        },
                    )
                }
            } // Enhanced-specific settings
            item {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = tile?.showEntityName == true,
                    onCheckedChange = {
                        tile?.let { current -> onNameEnabled(current.id, it) }
                    },
                    label = { Text(stringResource(commonR.string.setting_entity_name_on_tile)) },
                    icon = {
                        Image(
                            imageVector = (
                                if (tile?.showEntityName ==
                                    true
                                ) {
                                    Mdi.Alphabetical
                                } else {
                                    Mdi.AlphabeticalOff
                                }
                                ).rememberImageVector(),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getSwitchButtonColors(),
                )
            }
        }
    }
}
