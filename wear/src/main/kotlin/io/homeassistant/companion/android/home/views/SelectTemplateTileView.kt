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
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SelectTemplateTileView(templateTiles: Map<Int, TemplateTileConfig>, onSelectTemplateTile: (tileId: Int) -> Unit) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.template_tiles)
            }
            if (templateTiles.isEmpty()) {
                item {
                    Text(
                        text = stringResource(commonR.string.template_tile_no_tiles_yet),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                itemsIndexed(templateTiles.keys.toList()) { index, templateTileId ->
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        label = {
                            Text(stringResource(commonR.string.template_tile_n, index + 1))
                        },
                        onClick = { onSelectTemplateTile(templateTileId) },
                        colors = getFilledTonalButtonColors(),
                    )
                }
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSelectTemplateTileView() {
    SelectTemplateTileView(
        templateTiles = mapOf(
            -1 to TemplateTileConfig("Old template", 0),
            1111 to TemplateTileConfig("New template #1", 10),
            2222 to TemplateTileConfig("New template #2", 20),
        ),
        onSelectTemplateTile = {},
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSelectTemplateTileEmptyView() {
    SelectTemplateTileView(
        templateTiles = mapOf(),
        onSelectTemplateTile = {},
    )
}
