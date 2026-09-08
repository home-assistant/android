package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.database.wear.CameraTile
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.tiles.CameraTile.Companion.DEFAULT_REFRESH_INTERVAL
import io.homeassistant.companion.android.util.intervalToString
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SetCameraTileView(
    tile: CameraTile?,
    entityItem: EntityDisplay?,
    onSelectEntity: () -> Unit,
    onSelectRefreshInterval: () -> Unit,
) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(commonR.string.camera_tile)
            }
            item {
                val icon = entityItem?.icon ?: CommunityMaterial.Icon3.cmd_video
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
        }
    }
}
