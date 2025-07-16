package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.wear.tooling.preview.devices.WearDevices
import com.mikepenz.iconics.compose.Image
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.simplifiedEntity
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SetShortcutsTileView(shortcutEntities: List<SimplifiedEntity>, onShortcutEntitySelectionChange: (Int) -> Unit) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.shortcuts_choose)
            }
            items(shortcutEntities.size) { index ->

                val iconBitmap = getIcon(
                    shortcutEntities[index].icon,
                    shortcutEntities[index].domain,
                    LocalContext.current,
                )

                Button(
                    modifier = Modifier
                        .fillMaxWidth(),
                    icon = {
                        Image(
                            iconBitmap,
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(commonR.string.shortcut_n, index + 1),
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = shortcutEntities[index].friendlyName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = { onShortcutEntitySelectionChange(index) },
                    colors = getFilledTonalButtonColors(),
                )
            }
            if (shortcutEntities.size < 7) {
                item {
                    FilledIconButton(
                        modifier = Modifier.padding(
                            top = 16.dp,
                        ).touchTargetAwareSize(IconButtonDefaults.SmallButtonSize),
                        onClick = { onShortcutEntitySelectionChange(shortcutEntities.size) },
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(id = commonR.string.add_shortcut),
                            modifier = Modifier.size(
                                IconButtonDefaults.iconSizeFor(IconButtonDefaults.SmallButtonSize),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSetTileShortcutsView() {
    SetShortcutsTileView(
        shortcutEntities = mutableListOf(simplifiedEntity),
        onShortcutEntitySelectionChange = {},
    )
}
