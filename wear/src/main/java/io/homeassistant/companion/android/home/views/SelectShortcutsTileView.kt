package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.itemsIndexed
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.wear.tiles.ShortcutsTileId
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SelectShortcutsTileView(
    onSelectShortcutsTile: (ShortcutsTileId) -> Unit,
    isShowShortcutTextEnabled: Boolean,
    onShowShortcutTextEnabled: (Boolean) -> Unit
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
                    ListHeader(id = commonR.string.shortcut_tiles)
                }
                item {
                    ToggleChip(
                        modifier = Modifier.fillMaxWidth(),
                        checked = isShowShortcutTextEnabled,
                        onCheckedChange = { onShowShortcutTextEnabled(it) },
                        label = {
                            Text(stringResource(commonR.string.shortcuts_tile_text_setting))
                        },
                        appIcon = {
                            Image(
                                asset =
                                if (isShowShortcutTextEnabled) {
                                    CommunityMaterial.Icon.cmd_alphabetical
                                } else {
                                    CommunityMaterial.Icon.cmd_alphabetical_off
                                },
                                colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                            )
                        },
                        toggleControl = {
                            Icon(
                                imageVector = ToggleChipDefaults.checkboxIcon(isShowShortcutTextEnabled),
                                contentDescription = if (isShowShortcutTextEnabled) {
                                    stringResource(commonR.string.show)
                                } else {
                                    stringResource(commonR.string.hide)
                                }
                            )
                        }
                    )
                }
                item {
                    ListHeader(id = commonR.string.shortcuts_tile_select)
                }
                itemsIndexed(ShortcutsTileId.values().toList()) { index, shortcutsTileId ->
                    Chip(
                        modifier = Modifier
                            .fillMaxWidth(),
                        icon = {
                            Image(
                                CommunityMaterial.Icon.cmd_bookmark,
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        },
                        label = {
                            Text(stringResource(commonR.string.shortcuts_tile_n, index + 1))
                        },
                        onClick = { onSelectShortcutsTile(shortcutsTileId) },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }
    }
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
private fun PreviewSelectShortcutsTileView() {
    SelectShortcutsTileView(
        onSelectShortcutsTile = {},
        isShowShortcutTextEnabled = true,
        onShowShortcutTextEnabled = {}
    )
}
