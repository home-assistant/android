package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.wear.ThermostatTile
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SelectThermostatTileView(tiles: List<ThermostatTile>, onSelectTile: (tileId: Int) -> Unit) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.thermostat_tiles)
            }
            if (tiles.isEmpty()) {
                item {
                    Text(
                        text = stringResource(commonR.string.thermostat_tile_no_tiles_yet),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                itemsIndexed(tiles, key = { _, item -> "tile.${item.id}" }) { index, tile ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(commonR.string.thermostat_tile_n, index + 1)) },
                        secondaryLabel = if (tile.entityId != null) {
                            { Text(tile.entityId!!) }
                        } else {
                            null
                        },
                        onClick = { onSelectTile(tile.id) },
                        colors = getFilledTonalButtonColors(),
                    )
                }
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSelectThermostatTileViewOne() {
    SelectThermostatTileView(
        tiles = listOf(
            ThermostatTile(
                id = 1,
                entityId = "climate.living_room",
                refreshInterval = 300,
                targetTemperature = 21.0f,
                showEntityName = true,
            ),
        ),
        onSelectTile = {},
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSelectThermostatTileViewEmpty() {
    SelectThermostatTileView(tiles = emptyList(), onSelectTile = {})
}
