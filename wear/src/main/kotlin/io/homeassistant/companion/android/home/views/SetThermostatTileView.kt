package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
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
            temperatureUnitSetting(tile, onPreferencesChanged)
            if (tile?.layoutStyle == "enhanced") {
                enhancedSettings(tile, onPreferencesChanged)
            }
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

private fun ScalingLazyListScope.temperatureUnitSetting(
    tile: ThermostatTile?,
    onPreferencesChanged: (Int, (ThermostatTile) -> ThermostatTile) -> Unit,
) {
    item {
        Button(
            modifier = Modifier.fillMaxWidth(),
            colors = getFilledTonalButtonColors(),
            label = { Text(stringResource(commonR.string.setting_thermostat_temperature_unit)) },
            secondaryLabel = {
                Text(
                    stringResource(
                        when (tile?.temperatureUnit) {
                            "C" -> commonR.string.setting_thermostat_temperature_unit_celsius
                            "F" -> commonR.string.setting_thermostat_temperature_unit_fahrenheit
                            else -> commonR.string.setting_thermostat_temperature_unit_default
                        },
                    ),
                )
            },
            onClick = {
                tile?.let {
                    onPreferencesChanged(it.id) { current ->
                        val next = when (current.temperatureUnit) {
                            "C" -> "F"
                            "F" -> "default"
                            else -> "C"
                        }
                        current.copy(temperatureUnit = next)
                    }
                }
            },
        )
    }
}

private fun ScalingLazyListScope.enhancedSettings(
    tile: ThermostatTile,
    onPreferencesChanged: (Int, (ThermostatTile) -> ThermostatTile) -> Unit,
) {
    settingSwitch(commonR.string.setting_thermostat_hvac_modes, tile.showHvacModes) { enabled ->
        onPreferencesChanged(tile.id) { it.copy(showHvacModes = enabled) }
    }
    settingSwitch(commonR.string.setting_thermostat_fan_control, tile.showFanControl) { enabled ->
        onPreferencesChanged(tile.id) { it.copy(showFanControl = enabled) }
    }
    settingSwitch(commonR.string.setting_thermostat_activity, tile.showActivity) { enabled ->
        onPreferencesChanged(tile.id) { it.copy(showActivity = enabled) }
    }
    secondaryAdjustmentSettings(tile, onPreferencesChanged)
}

private fun ScalingLazyListScope.secondaryAdjustmentSettings(
    tile: ThermostatTile,
    onPreferencesChanged: (Int, (ThermostatTile) -> ThermostatTile) -> Unit,
) {
    settingSwitch(commonR.string.setting_thermostat_secondary_buttons, tile.showSecondaryButtons) { enabled ->
        onPreferencesChanged(tile.id) { it.copy(showSecondaryButtons = enabled) }
    }
    if (!tile.showSecondaryButtons) return
    item {
        Button(
            modifier = Modifier.fillMaxWidth(),
            colors = getFilledTonalButtonColors(),
            label = { Text(stringResource(commonR.string.setting_thermostat_secondary_step)) },
            secondaryLabel = {
                Text(
                    stringResource(
                        commonR.string.setting_thermostat_secondary_step_summary,
                        tile.secondaryStep,
                    ),
                )
            },
            onClick = {
                onPreferencesChanged(tile.id) { current ->
                    val choices = listOf(3, 5, 2, 10)
                    val next = choices[(choices.indexOf(current.secondaryStep) + 1) % choices.size]
                    current.copy(secondaryStep = next)
                }
            },
        )
    }
}

private fun ScalingLazyListScope.settingSwitch(label: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    item {
        SwitchButton(
            modifier = Modifier.fillMaxWidth(),
            checked = checked,
            onCheckedChange = onCheckedChange,
            label = { Text(stringResource(label)) },
            colors = getSwitchButtonColors(),
        )
    }
}
