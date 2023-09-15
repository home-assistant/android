package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.wear.CameraTile
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.tiles.CameraTile.Companion.DEFAULT_REFRESH_INTERVAL
import io.homeassistant.companion.android.util.intervalToString
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SetCameraTileView(
    tile: CameraTile?,
    onSelectEntity: () -> Unit,
    onSelectRefreshInterval: () -> Unit
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
                    ListHeader(commonR.string.camera_tile)
                }
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Image(
                                asset = CommunityMaterial.Icon3.cmd_video, // TODO
                                colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        label = {
                            Text(
                                text = stringResource(id = R.string.choose_entity)
                            )
                        },
                        secondaryLabel = {
                            Text(tile?.entityId ?: "")
                        },
                        onClick = onSelectEntity
                    )
                }

                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Image(
                                asset = CommunityMaterial.Icon3.cmd_timer_cog,
                                colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        label = {
                            Text(
                                text = stringResource(id = R.string.refresh_interval)
                            )
                        },
                        secondaryLabel = {
                            Text(
                                intervalToString(LocalContext.current, (tile?.refreshInterval ?: DEFAULT_REFRESH_INTERVAL).toInt())
                            )
                        },
                        onClick = onSelectRefreshInterval
                    )
                }
            }
        }
    }
}
