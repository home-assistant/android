package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.previewFavoritesList

@Composable
fun SettingsView(
    favorites: List<String>,
    onClickSetFavorites: () -> Unit,
    onClearFavorites: () -> Unit,
    onClickSetShortcuts: () -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean,
    onHapticEnabled: (Boolean) -> Unit,
    onToastEnabled: (Boolean) -> Unit
) {
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    RotaryEventState(scrollState = scalingLazyListState)

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            top = 40.dp,
            start = 8.dp,
            end = 8.dp,
            bottom = 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = scalingLazyListState
    ) {
        item {
            ListHeader(id = R.string.settings)
        }
        item {
            Chip(
                modifier = Modifier
                    .fillMaxWidth(),
                icon = {
                    Image(asset = CommunityMaterial.Icon3.cmd_star)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.favorite)
                    )
                },
                onClick = onClickSetFavorites,
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                )
            )
        }
        item {
            Chip(
                modifier = Modifier
                    .fillMaxWidth(),
                icon = {
                    Image(asset = CommunityMaterial.Icon.cmd_delete)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.clear_favorites),
                    )
                },
                onClick = onClearFavorites,
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                ),
                secondaryLabel = {
                    Text(
                        text = stringResource(id = R.string.irreverisble)
                    )
                },
                enabled = favorites.isNotEmpty()
            )
        }

        item {
            val haptic = LocalHapticFeedback.current
            ToggleChip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                checked = isHapticEnabled,
                onCheckedChange = {
                    onHapticEnabled(it)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                label = {
                    Text(stringResource(R.string.setting_haptic_label))
                },
                appIcon = {
                    Image(
                        asset =
                        if (isHapticEnabled)
                            CommunityMaterial.Icon3.cmd_watch_vibrate
                        else
                            CommunityMaterial.Icon3.cmd_watch_vibrate_off
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors(
                    checkedStartBackgroundColor = Color(0xFFAECBFA),
                    checkedEndBackgroundColor = Color(0xFFAECBFA),
                    uncheckedStartBackgroundColor = Color(0xFFAECBFA),
                    uncheckedEndBackgroundColor = Color(0xFFAECBFA),
                    checkedContentColor = Color.Black,
                    uncheckedContentColor = Color.Black,
                )
            )
        }
        item {
            ToggleChip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                checked = isToastEnabled,
                onCheckedChange = {
                    onToastEnabled(it)
                },
                label = {
                    Text(stringResource(R.string.setting_toast_label))
                },
                appIcon = {
                    Image(
                        asset =
                        if (isToastEnabled)
                            CommunityMaterial.Icon3.cmd_message
                        else
                            CommunityMaterial.Icon3.cmd_message_off
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors(
                    checkedStartBackgroundColor = Color(0xFFAECBFA),
                    checkedEndBackgroundColor = Color(0xFFAECBFA),
                    uncheckedStartBackgroundColor = Color(0xFFAECBFA),
                    uncheckedEndBackgroundColor = Color(0xFFAECBFA),
                    checkedContentColor = Color.Black,
                    uncheckedContentColor = Color.Black,
                )
            )
        }

        item {
            ListHeader(
                id = R.string.tile_settings,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        item {
            Chip(
                modifier = Modifier
                    .fillMaxWidth(),
                icon = {
                    Image(asset = CommunityMaterial.Icon3.cmd_star_circle_outline)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.shortcuts)
                    )
                },
                onClick = onClickSetShortcuts,
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black

                )
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSettingsView() {
    val rotaryEventDispatcher = RotaryEventDispatcher()

    CompositionLocalProvider(
        LocalRotaryEventDispatcher provides rotaryEventDispatcher
    ) {
        SettingsView(
            favorites = previewFavoritesList,
            onClickSetFavorites = { /*TODO*/ },
            onClearFavorites = {},
            onClickSetShortcuts = {},
            isHapticEnabled = true,
            isToastEnabled = false,
            {},
            {}
        )
    }
}
