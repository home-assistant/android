package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ToggleButton
import androidx.wear.tooling.preview.devices.WearDevices
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.theme.getToggleButtonColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.util.ToggleCheckbox
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SelectShortcutsTileView(
    shortcutTileEntitiesCountById: Map<Int?, Int>,
    onSelectShortcutsTile: (tileId: Int?) -> Unit,
    isShowShortcutTextEnabled: Boolean,
    onShowShortcutTextEnabled: (Boolean) -> Unit
) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.shortcut_tiles)
            }
            item {
                ToggleButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = isShowShortcutTextEnabled,
                    onCheckedChange = { onShowShortcutTextEnabled(it) },
                    label = { Text(stringResource(commonR.string.shortcuts_tile_text_setting)) },
                    icon = {
                        Image(
                            asset =
                            if (isShowShortcutTextEnabled) {
                                CommunityMaterial.Icon.cmd_alphabetical
                            } else {
                                CommunityMaterial.Icon.cmd_alphabetical_off
                            },
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
                        )
                    },
                    toggleControl = { ToggleCheckbox(isShowShortcutTextEnabled) },
                    colors = getToggleButtonColors()
                )
            }
            item {
                ListHeader(id = commonR.string.shortcuts_tile_select)
            }
            if (shortcutTileEntitiesCountById.isEmpty()) {
                item {
                    Text(stringResource(commonR.string.shortcuts_tile_no_tiles_yet))
                }
            } else {
                itemsIndexed(shortcutTileEntitiesCountById.keys.toList()) { index, shortcutsTileId ->
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(),
                        label = { Text(stringResource(commonR.string.shortcuts_tile_n, index + 1)) },
                        secondaryLabel = {
                            val entityCount = shortcutTileEntitiesCountById[shortcutsTileId] ?: 0
                            if (entityCount > 0) {
                                Text(pluralStringResource(commonR.plurals.n_entities, entityCount, entityCount))
                            }
                        },
                        onClick = { onSelectShortcutsTile(shortcutsTileId) },
                        colors = getFilledTonalButtonColors()
                    )
                }
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSelectShortcutsTileView() {
    SelectShortcutsTileView(
        shortcutTileEntitiesCountById = mapOf(
            null to 7,
            1111 to 1,
            2222 to 0
        ),
        onSelectShortcutsTile = {},
        isShowShortcutTextEnabled = true,
        onShowShortcutTextEnabled = {}
    )
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSelectShortcutsTileEmptyView() {
    SelectShortcutsTileView(
        shortcutTileEntitiesCountById = emptyMap(),
        onSelectShortcutsTile = {},
        isShowShortcutTextEnabled = true,
        onShowShortcutTextEnabled = {}
    )
}
