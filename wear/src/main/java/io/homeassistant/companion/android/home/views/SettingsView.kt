package io.homeassistant.companion.android.home.views

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.previewFavoritesList
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SecondarySettingsChip(
    icon: IIcon,
    label: String,
    secondaryLabel: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        icon = {
            Image(
                asset = icon,
                colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
        label = { Text(text = label) },
        secondaryLabel = secondaryLabel?.let {
            { Text(text = secondaryLabel) }
        },
        enabled = enabled,
        onClick = onClick
    )
}

@ExperimentalComposeUiApi
@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Composable
fun SettingsView(
    favorites: List<String>,
    onClickSetFavorites: () -> Unit,
    onClearFavorites: () -> Unit,
    onClickSetShortcuts: () -> Unit,
    onClickLogout: () -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean,
    onHapticEnabled: (Boolean) -> Unit,
    onToastEnabled: (Boolean) -> Unit,
    onClickTemplateTile: () -> Unit
) {
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    LocalView.current.requestFocus()

    WearAppTheme {
        Scaffold(
            positionIndicator = {
                if (scalingLazyListState.isScrollInProgress)
                    PositionIndicator(scalingLazyListState = scalingLazyListState)
            },
            timeText = { TimeText(!scalingLazyListState.isScrollInProgress) }
        ) {
            ThemeLazyColumn(
                state = scalingLazyListState
            ) {
                item {
                    ListHeader(id = commonR.string.favorite_settings)
                }
                item {
                    SecondarySettingsChip(
                        icon = CommunityMaterial.Icon3.cmd_star,
                        label = stringResource(commonR.string.favorite),
                        onClick = onClickSetFavorites
                    )
                }
                item {
                    SecondarySettingsChip(
                        icon = CommunityMaterial.Icon.cmd_delete,
                        label = stringResource(commonR.string.clear_favorites),
                        secondaryLabel = stringResource(commonR.string.irreversible),
                        enabled = favorites.isNotEmpty(),
                        onClick = onClearFavorites
                    )
                }
                item {
                    ListHeader(
                        id = commonR.string.feedback_settings
                    )
                }
                item {
                    val haptic = LocalHapticFeedback.current
                    ToggleChip(
                        modifier = Modifier.fillMaxWidth(),
                        checked = isHapticEnabled,
                        onCheckedChange = {
                            onHapticEnabled(it)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        label = {
                            Text(stringResource(commonR.string.setting_haptic_label))
                        },
                        appIcon = {
                            Image(
                                asset =
                                if (isHapticEnabled)
                                    CommunityMaterial.Icon3.cmd_watch_vibrate
                                else
                                    CommunityMaterial.Icon3.cmd_watch_vibrate_off,
                                colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                            )
                        }
                    )
                }
                item {
                    ToggleChip(
                        modifier = Modifier.fillMaxWidth(),
                        checked = isToastEnabled,
                        onCheckedChange = onToastEnabled,
                        label = {
                            Text(stringResource(commonR.string.setting_toast_label))
                        },
                        appIcon = {
                            Image(
                                asset =
                                if (isToastEnabled)
                                    CommunityMaterial.Icon3.cmd_message
                                else
                                    CommunityMaterial.Icon3.cmd_message_off,
                                colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                            )
                        }
                    )
                }

                item {
                    ListHeader(
                        id = commonR.string.tile_settings
                    )
                }
                item {
                    SecondarySettingsChip(
                        icon = CommunityMaterial.Icon3.cmd_star_circle_outline,
                        label = stringResource(commonR.string.shortcuts_tile),
                        onClick = onClickSetShortcuts
                    )
                }
                item {
                    SecondarySettingsChip(
                        icon = CommunityMaterial.Icon3.cmd_text_box,
                        label = stringResource(commonR.string.template_tile),
                        onClick = onClickTemplateTile
                    )
                }

                item {
                    ListHeader(
                        id = commonR.string.account
                    )
                }
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Image(asset = CommunityMaterial.Icon.cmd_exit_run)
                        },
                        label = {
                            Text(
                                text = stringResource(id = commonR.string.logout)
                            )
                        },
                        onClick = onClickLogout,
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = Color.Red,
                            contentColor = Color.Black
                        )
                    )
                }
            }
        }
    }
}

@ExperimentalComposeUiApi
@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Preview
@Composable
private fun PreviewSettingsView() {
    SettingsView(
        favorites = previewFavoritesList,
        onClickSetFavorites = { /*TODO*/ },
        onClearFavorites = {},
        onClickSetShortcuts = {},
        onClickLogout = {},
        isHapticEnabled = true,
        isToastEnabled = false,
        onHapticEnabled = {},
        onToastEnabled = {},
        onClickTemplateTile = {}
    )
}
