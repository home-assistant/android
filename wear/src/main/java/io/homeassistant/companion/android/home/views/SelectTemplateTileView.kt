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
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SelectTemplateTileView(
    templateTileIds: List<Int?>,
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
                if (templateTileIds.isEmpty()) {
                    item {
                        Text(stringResource(commonR.string.template_tile_no_tiles_yet))
                    }
                } else {
                    itemsIndexed(templateTileIds) { index, templateTileId ->
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
        templateTileIds = listOf(
            null,
            1111,
            2222
        ),
        onSelectTemplateTile = {}
    )
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
private fun PreviewSelectTemplateTileEmptyView() {
    SelectTemplateTileView(
        templateTileIds = emptyList(),
        onSelectTemplateTile = {}
    )
}
