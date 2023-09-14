package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import io.homeassistant.companion.android.database.wear.CameraSnapshotTile
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SelectCameraSnapshotTileView(
    tiles: List<CameraSnapshotTile>,
    onSelectTile: (tileId: Int) -> Unit
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
                    ListHeader(id = commonR.string.camera_snapshot_tiles)
                }
                if (tiles.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(commonR.string.camera_snapshot_tile_no_tiles_yet),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    itemsIndexed(tiles, key = { _, item -> "tile.${item.id}" }) { index, tile ->
                        Chip(
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(stringResource(commonR.string.camera_snapshot_tile_n, index + 1))
                            },
                            secondaryLabel = if (tile.entityId != null) {
                                { Text(tile.entityId!!) }
                            } else {
                                null
                            },
                            onClick = { onSelectTile(tile.id) },
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
private fun PreviewSelectCameraSnapshotTileViewOne() {
    SelectCameraSnapshotTileView(
        tiles = listOf(
            CameraSnapshotTile(id = 1, entityId = "camera.buienradar", refreshInterval = 300)
        ),
        onSelectTile = {}
    )
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
private fun PreviewSelectCameraSnapshotTileViewEmpty() {
    SelectCameraSnapshotTileView(tiles = emptyList(), onSelectTile = {})
}
