package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.getIcon
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
    entities: List<Entity>?,
    onSelectEntity: () -> Unit,
    onSelectRefreshInterval: () -> Unit,
    onNameEnabled: (Int, Boolean) -> Unit,
) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(commonR.string.thermostat_tile)
            }
            item {
                val entity = tile?.entityId?.let { tileEntityId ->
                    entities?.firstOrNull { it.entityId == tileEntityId }
                }
                val icon = entity?.getIcon(LocalContext.current) ?: CommunityMaterial.Icon3.cmd_thermostat
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Image(
                            asset = icon,
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
                        Text(entity?.friendlyName ?: tile?.entityId ?: "")
                    },
                    onClick = onSelectEntity,
                )
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Image(
                            asset = CommunityMaterial.Icon3.cmd_timer_cog,
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
                SwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = tile?.showEntityName == true,
                    onCheckedChange = {
                        onNameEnabled(tile?.id.toString().toInt(), it)
                    },
                    label = { Text(stringResource(commonR.string.setting_entity_name_on_tile)) },
                    icon = {
                        Image(
                            asset =
                            if (tile?.showEntityName == true) {
                                CommunityMaterial.Icon.cmd_alphabetical
                            } else {
                                CommunityMaterial.Icon.cmd_alphabetical_off
                            },
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getSwitchButtonColors(),
                )
            }
        }
    }
}
