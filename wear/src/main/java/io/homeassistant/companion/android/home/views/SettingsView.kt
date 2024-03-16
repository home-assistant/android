package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ToggleButton
import androidx.wear.tooling.preview.devices.WearDevices
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.theme.getToggleButtonColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.util.ToggleSwitch
import io.homeassistant.companion.android.util.previewFavoritesList
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SecondarySettingsChip(
    icon: IIcon,
    label: String,
    secondaryLabel: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        icon = {
            Image(
                asset = icon,
                colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
            )
        },
        colors = getFilledTonalButtonColors(),
        label = { Text(label) },
        secondaryLabel = secondaryLabel?.let {
            { Text(text = secondaryLabel) }
        },
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
fun SettingsView(
    loadingState: MainViewModel.LoadingState,
    favorites: List<String>,
    onClickSetFavorites: () -> Unit,
    onClearFavorites: () -> Unit,
    onClickSetShortcuts: () -> Unit,
    onClickSensors: () -> Unit,
    onClickLogout: () -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean,
    isFavoritesOnly: Boolean,
    isAssistantAppAllowed: Boolean,
    onHapticEnabled: (Boolean) -> Unit,
    onToastEnabled: (Boolean) -> Unit,
    setFavoritesOnly: (Boolean) -> Unit,
    onClickCameraTile: () -> Unit,
    onClickTemplateTiles: () -> Unit,
    onAssistantAppAllowed: (Boolean) -> Unit
) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.favorites)
            }
            item {
                SecondarySettingsChip(
                    icon = CommunityMaterial.Icon3.cmd_star,
                    label = stringResource(commonR.string.favorite),
                    enabled = loadingState == MainViewModel.LoadingState.READY,
                    onClick = onClickSetFavorites
                )
            }
            item {
                SecondarySettingsChip(
                    icon = CommunityMaterial.Icon.cmd_delete,
                    label = stringResource(commonR.string.clear_favorites),
                    enabled = favorites.isNotEmpty(),
                    onClick = onClearFavorites
                )
            }
            item {
                ToggleButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = isFavoritesOnly,
                    onCheckedChange = { setFavoritesOnly(it) },
                    label = { Text(stringResource(commonR.string.only_favorites)) },
                    enabled = favorites.isNotEmpty(),
                    toggleControl = { ToggleSwitch(isFavoritesOnly) },
                    icon = {
                        Image(
                            asset = CommunityMaterial.Icon2.cmd_home_heart,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
                        )
                    },
                    colors = getToggleButtonColors()
                )
            }
            item {
                ListHeader(
                    id = commonR.string.feedback
                )
            }
            item {
                val haptic = LocalHapticFeedback.current
                ToggleButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = isHapticEnabled,
                    onCheckedChange = {
                        onHapticEnabled(it)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    label = { Text(stringResource(commonR.string.setting_haptic_label)) },
                    icon = {
                        Image(
                            asset =
                            if (isHapticEnabled) {
                                CommunityMaterial.Icon3.cmd_watch_vibrate
                            } else {
                                CommunityMaterial.Icon3.cmd_watch_vibrate_off
                            },
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
                        )
                    },
                    toggleControl = { ToggleSwitch(isHapticEnabled) },
                    colors = getToggleButtonColors()
                )
            }
            item {
                ToggleButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = isToastEnabled,
                    onCheckedChange = onToastEnabled,
                    label = { Text(stringResource(commonR.string.setting_toast_label)) },
                    icon = {
                        Image(
                            asset =
                            if (isToastEnabled) {
                                CommunityMaterial.Icon3.cmd_message
                            } else {
                                CommunityMaterial.Icon3.cmd_message_off
                            },
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
                        )
                    },
                    toggleControl = { ToggleSwitch(isToastEnabled) },
                    colors = getToggleButtonColors()
                )
            }

            item {
                ListHeader(
                    id = commonR.string.tiles
                )
            }
            item {
                SecondarySettingsChip(
                    icon = CommunityMaterial.Icon3.cmd_video_box,
                    label = stringResource(commonR.string.camera_tiles),
                    onClick = onClickCameraTile
                )
            }
            item {
                SecondarySettingsChip(
                    icon = CommunityMaterial.Icon3.cmd_star_circle_outline,
                    label = stringResource(commonR.string.shortcut_tiles),
                    onClick = onClickSetShortcuts
                )
            }
            item {
                SecondarySettingsChip(
                    icon = CommunityMaterial.Icon3.cmd_text_box,
                    label = stringResource(commonR.string.template_tiles),
                    onClick = onClickTemplateTiles
                )
            }
            item {
                ListHeader(
                    id = commonR.string.sensors
                )
            }
            item {
                SecondarySettingsChip(
                    icon = CommunityMaterial.Icon2.cmd_leak,
                    label = stringResource(id = commonR.string.sensor_title),
                    onClick = onClickSensors
                )
            }
            item {
                ListHeader(
                    id = commonR.string.assist
                )
            }
            item {
                ToggleButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = isAssistantAppAllowed,
                    onCheckedChange = onAssistantAppAllowed,
                    label = { Text(stringResource(commonR.string.available_as_assistant_app)) },
                    icon = {
                        Image(
                            asset = CommunityMaterial.Icon.cmd_comment_processing_outline,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
                        )
                    },
                    toggleControl = { ToggleSwitch(isAssistantAppAllowed) },
                    colors = getToggleButtonColors()
                )
            }
            item {
                ListHeader(
                    id = commonR.string.account
                )
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Image(CommunityMaterial.Icon.cmd_exit_run) },
                    label = { Text(stringResource(commonR.string.logout)) },
                    onClick = onClickLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        contentColor = Color.Black
                    )
                )
            }
            item {
                ListHeader(commonR.string.application_version)
            }
            item {
                Text(
                    text = BuildConfig.VERSION_NAME
                )
            }
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
private fun PreviewSettingsView() {
    SettingsView(
        loadingState = MainViewModel.LoadingState.READY,
        favorites = previewFavoritesList,
        onClickSetFavorites = { },
        onClearFavorites = {},
        onClickSetShortcuts = {},
        onClickSensors = {},
        onClickLogout = {},
        isHapticEnabled = true,
        isToastEnabled = false,
        isFavoritesOnly = false,
        isAssistantAppAllowed = true,
        onHapticEnabled = {},
        onToastEnabled = {},
        setFavoritesOnly = {},
        onClickCameraTile = {},
        onClickTemplateTiles = {},
        onAssistantAppAllowed = {}
    )
}
