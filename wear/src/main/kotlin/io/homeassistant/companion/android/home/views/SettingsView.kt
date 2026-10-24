package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.Image
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
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.BellRing
import io.github.timoptr.mdiicons.generated.CommentProcessingOutline
import io.github.timoptr.mdiicons.generated.Delete
import io.github.timoptr.mdiicons.generated.ExitRun
import io.github.timoptr.mdiicons.generated.HomeHeart
import io.github.timoptr.mdiicons.generated.Leak
import io.github.timoptr.mdiicons.generated.Message
import io.github.timoptr.mdiicons.generated.MessageOff
import io.github.timoptr.mdiicons.generated.Star
import io.github.timoptr.mdiicons.generated.StarCircleOutline
import io.github.timoptr.mdiicons.generated.TextBox
import io.github.timoptr.mdiicons.generated.Thermostat
import io.github.timoptr.mdiicons.generated.VideoBox
import io.github.timoptr.mdiicons.generated.WatchVibrate
import io.github.timoptr.mdiicons.generated.WatchVibrateOff
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.theme.getSwitchButtonColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.util.previewFavoritesList
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SecondarySettingsChip(
    icon: MdiIcon,
    label: String,
    secondaryLabel: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        icon = {
            Image(
                imageVector = icon.rememberImageVector(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
            )
        },
        colors = getFilledTonalButtonColors(),
        label = { Text(label) },
        secondaryLabel = secondaryLabel?.let {
            { Text(text = secondaryLabel) }
        },
        enabled = enabled,
        onClick = onClick,
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
    areNotificationsAllowed: Boolean,
    onHapticEnabled: (Boolean) -> Unit,
    onToastEnabled: (Boolean) -> Unit,
    setFavoritesOnly: (Boolean) -> Unit,
    onClickCameraTile: () -> Unit,
    onClickTemplateTiles: () -> Unit,
    onClickThermostatTiles: () -> Unit,
    onAssistantAppAllowed: (Boolean) -> Unit,
    onClickNotifications: () -> Unit,
) {
    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.favorites)
            }
            item {
                SecondarySettingsChip(
                    icon = Mdi.Star,
                    label = stringResource(commonR.string.favorite),
                    enabled = loadingState == MainViewModel.LoadingState.READY,
                    onClick = onClickSetFavorites,
                )
            }
            item {
                SecondarySettingsChip(
                    icon = Mdi.Delete,
                    label = stringResource(commonR.string.clear_favorites),
                    enabled = favorites.isNotEmpty(),
                    onClick = onClearFavorites,
                )
            }
            item {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = isFavoritesOnly,
                    onCheckedChange = { setFavoritesOnly(it) },
                    label = { Text(stringResource(commonR.string.only_favorites)) },
                    enabled = favorites.isNotEmpty(),
                    icon = {
                        Image(
                            imageVector = Mdi.HomeHeart.rememberImageVector(),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getSwitchButtonColors(),
                )
            }
            item {
                ListHeader(
                    id = commonR.string.feedback,
                )
            }
            item {
                val haptic = LocalHapticFeedback.current
                SwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = isHapticEnabled,
                    onCheckedChange = {
                        onHapticEnabled(it)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    label = { Text(stringResource(commonR.string.setting_haptic_label)) },
                    icon = {
                        Image(
                            imageVector = (if (isHapticEnabled) Mdi.WatchVibrate else Mdi.WatchVibrateOff)
                                .rememberImageVector(),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getSwitchButtonColors(),
                )
            }
            item {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = isToastEnabled,
                    onCheckedChange = onToastEnabled,
                    label = { Text(stringResource(commonR.string.setting_toast_label)) },
                    icon = {
                        Image(
                            imageVector = (if (isToastEnabled) Mdi.Message else Mdi.MessageOff).rememberImageVector(),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getSwitchButtonColors(),
                )
            }

            item {
                ListHeader(
                    id = commonR.string.tiles,
                )
            }
            item {
                SecondarySettingsChip(
                    icon = Mdi.VideoBox,
                    label = stringResource(commonR.string.camera_tiles),
                    onClick = onClickCameraTile,
                )
            }
            item {
                SecondarySettingsChip(
                    icon = Mdi.StarCircleOutline,
                    label = stringResource(commonR.string.shortcut_tiles),
                    onClick = onClickSetShortcuts,
                )
            }
            item {
                SecondarySettingsChip(
                    icon = Mdi.TextBox,
                    label = stringResource(commonR.string.template_tiles),
                    onClick = onClickTemplateTiles,
                )
            }
            item {
                SecondarySettingsChip(
                    icon = Mdi.Thermostat,
                    label = stringResource(commonR.string.thermostat_tiles),
                    onClick = onClickThermostatTiles,
                )
            }
            item {
                ListHeader(
                    id = commonR.string.sensors,
                )
            }
            item {
                SecondarySettingsChip(
                    icon = Mdi.Leak,
                    label = stringResource(id = commonR.string.sensor_title),
                    onClick = onClickSensors,
                )
            }
            item {
                ListHeader(
                    id = commonR.string.assist,
                )
            }
            item {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    checked = isAssistantAppAllowed,
                    onCheckedChange = onAssistantAppAllowed,
                    label = { Text(stringResource(commonR.string.available_as_assistant_app)) },
                    icon = {
                        Image(
                            imageVector = Mdi.CommentProcessingOutline.rememberImageVector(),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface),
                        )
                    },
                    colors = getSwitchButtonColors(),
                )
            }
            if (!areNotificationsAllowed) {
                item {
                    ListHeader(
                        id = commonR.string.notifications,
                    )
                }
                item {
                    SecondarySettingsChip(
                        icon = Mdi.BellRing,
                        label = stringResource(commonR.string.suggestion_notifications_title),
                        onClick = onClickNotifications,
                    )
                }
            }
            item {
                ListHeader(
                    id = commonR.string.account,
                )
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Image(imageVector = Mdi.ExitRun.rememberImageVector(), contentDescription = null) },
                    label = { Text(stringResource(commonR.string.logout)) },
                    onClick = onClickLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        contentColor = Color.Black,
                    ),
                )
            }
            item {
                ListHeader(commonR.string.application_version)
            }
            item {
                Text(
                    text = BuildConfig.VERSION_NAME,
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
        areNotificationsAllowed = false,
        onHapticEnabled = {},
        onToastEnabled = {},
        setFavoritesOnly = {},
        onClickCameraTile = {},
        onClickTemplateTiles = {},
        onClickThermostatTiles = {},
        onAssistantAppAllowed = {},
        onClickNotifications = {},
    )
}
