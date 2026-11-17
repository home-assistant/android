package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.FormatListChecks
import io.github.timoptr.mdiicons.generated.TimerCog
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.database.wear.TodoListTile
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.tiles.TodoListTile.Companion.DEFAULT_REFRESH_INTERVAL
import io.homeassistant.companion.android.util.intervalToString
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SetTodoListTileView(
    tile: TodoListTile?,
    entityItem: EntityDisplay?,
    onSelectEntity: () -> Unit,
    onSelectRefreshInterval: () -> Unit,
) {
    WearAppTheme {
        ThemeLazyColumn {
            item { ListHeader(commonR.string.todo_list_tile) }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Image(
                            imageVector = (entityItem?.icon ?: Mdi.FormatListChecks).rememberImageVector(),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getFilledTonalButtonColors(),
                    label = { Text(stringResource(id = commonR.string.choose_entity)) },
                    secondaryLabel = { Text((entityItem?.name ?: tile?.entityId).orEmpty()) },
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
                    label = { Text(stringResource(id = commonR.string.refresh_interval)) },
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
