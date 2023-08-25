package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.itemsIndexed
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.common.data.prefs.impl.entities.TemplateTileConfig
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SelectTemplateTileView(
    templateTiles: Map<Int?, TemplateTileConfig>,
    onSelectTemplateTile: (tileId: Int?) -> Unit
) {
    val scalingLazyListState = rememberScalingLazyListState()
    WearAppTheme {
        Scaffold(
            positionIndicator = {
                if (scalingLazyListState.isScrollInProgress) {
                    PositionIndicator(scalingLazyListState = scalingLazyListState)
                }
            },
            timeText = { TimeText(scalingLazyListState = scalingLazyListState) }
        ) {
            ThemeLazyColumn(state = scalingLazyListState) {
                item {
                    ListHeader(id = commonR.string.template_tiles)
                }
                item {
                    ListHeader(id = commonR.string.template_tile_select)
                }
                // TODO: make sure this refreshes whenever a new Template tile is added
                if (templateTiles.isEmpty()) {
                    item {
                        Text(stringResource(commonR.string.template_tile_no_tiles_yet))
                    }
                } else {
                    itemsIndexed(templateTiles.keys.toList()) { index, templateTileId ->
                        Chip(
                            modifier = Modifier
                                .fillMaxWidth(),
                            label = {
                                Text(stringResource(commonR.string.template_tile_n, index + 1))
                            },
                            onClick = { onSelectTemplateTile(templateTileId) },
                            colors = ChipDefaults.secondaryChipColors()
                        )
                    }
                }
            }
        }
    }
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
private fun PreviewSelectTemplateTileView() {
    SelectTemplateTileView(
        templateTiles = mapOf(
            null to TemplateTileConfig("Old template", 0),
            1111 to TemplateTileConfig("New template #1", 10),
            2222 to TemplateTileConfig("New template #2", 20)
        ),
        onSelectTemplateTile = {}
    )
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
private fun PreviewSelectTemplateTileEmptyView() {
    SelectTemplateTileView(
        templateTiles = mapOf(),
        onSelectTemplateTile = {}
    )
}
