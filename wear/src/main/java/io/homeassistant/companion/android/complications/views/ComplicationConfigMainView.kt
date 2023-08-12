package io.homeassistant.companion.android.complications.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.complications.ComplicationConfigViewModel
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.simplifiedEntity
import io.homeassistant.companion.android.views.ChooseEntityView
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn

private const val SCREEN_MAIN = "main"
private const val SCREEN_CHOOSE_ENTITY = "choose_entity"

@Composable
fun LoadConfigView(
    complicationConfigViewModel: ComplicationConfigViewModel,
    onAcceptClicked: () -> Unit
) {
    WearAppTheme {
        val swipeDismissableNavController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(
            navController = swipeDismissableNavController,
            startDestination = SCREEN_MAIN
        ) {
            composable(SCREEN_MAIN) {
                MainConfigView(
                    entity = complicationConfigViewModel.selectedEntity,
                    showTitle = complicationConfigViewModel.entityShowTitle,
                    showUnit = complicationConfigViewModel.entityShowUnit,
                    loadingState = complicationConfigViewModel.loadingState,
                    onChooseEntityClicked = {
                        swipeDismissableNavController.navigate(SCREEN_CHOOSE_ENTITY)
                    },
                    onShowTitleClicked = complicationConfigViewModel::setShowTitle,
                    onShowUnitClicked = complicationConfigViewModel::setShowUnit,
                    onAcceptClicked = onAcceptClicked
                )
            }
            composable(SCREEN_CHOOSE_ENTITY) {
                ChooseEntityView(
                    entitiesByDomainOrder = complicationConfigViewModel.entitiesByDomainOrder,
                    entitiesByDomain = complicationConfigViewModel.entitiesByDomain,
                    favoriteEntityIds = complicationConfigViewModel.favoriteEntityIds,
                    onNoneClicked = {},
                    onEntitySelected = { entity ->
                        complicationConfigViewModel.setEntity(entity)
                        swipeDismissableNavController.navigateUp()
                    },
                    allowNone = false
                )
            }
        }
    }
}

@Composable
fun MainConfigView(
    entity: SimplifiedEntity?,
    showTitle: Boolean,
    showUnit: Boolean,
    loadingState: ComplicationConfigViewModel.LoadingState,
    onChooseEntityClicked: () -> Unit,
    onShowTitleClicked: (Boolean) -> Unit,
    onShowUnitClicked: (Boolean) -> Unit,
    onAcceptClicked: () -> Unit
) {
    ThemeLazyColumn {
        item {
            ListHeader(id = R.string.complication_entity_state_label)
        }
        if (loadingState != ComplicationConfigViewModel.LoadingState.ERROR) {
            val loaded = loadingState == ComplicationConfigViewModel.LoadingState.READY
            item {
                val iconBitmap = getIcon(
                    entity?.icon,
                    entity?.domain ?: "light",
                    LocalContext.current
                )
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Image(
                            asset = iconBitmap ?: CommunityMaterial.Icon.cmd_bookmark,
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
                        Text(
                            if (loaded) {
                                entity?.friendlyName ?: ""
                            } else {
                                stringResource(R.string.loading)
                            }
                        )
                    },
                    enabled = loaded,
                    onClick = onChooseEntityClicked
                )
            }
            item {
                val isChecked = !loaded || showTitle
                ToggleChip(
                    checked = isChecked,
                    onCheckedChange = onShowTitleClicked,
                    label = { Text(stringResource(R.string.show_entity_title)) },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(isChecked),
                            contentDescription = if (isChecked) {
                                stringResource(R.string.enabled)
                            } else {
                                stringResource(R.string.disabled)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loaded && entity != null
                )
            }
            item {
                val isChecked = !loaded || showUnit
                ToggleChip(
                    checked = isChecked,
                    onCheckedChange = onShowUnitClicked,
                    label = { Text(stringResource(R.string.show_unit_title)) },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(isChecked),
                            contentDescription = if (isChecked) {
                                stringResource(R.string.enabled)
                            } else {
                                stringResource(R.string.disabled)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loaded && entity != null
                )
            }

            item {
                Button(
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = { onAcceptClicked() },
                    colors = ButtonDefaults.primaryButtonColors(),
                    enabled = loaded && entity != null
                ) {
                    Image(
                        CommunityMaterial.Icon.cmd_check
                    )
                }
            }
        } else {
            item {
                Text(text = stringResource(R.string.error_connection_failed))
            }
        }
    }
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND)
@Composable
fun PreviewMainConfigView() {
    MainConfigView(
        entity = simplifiedEntity,
        showTitle = true,
        showUnit = true,
        loadingState = ComplicationConfigViewModel.LoadingState.READY,
        onChooseEntityClicked = {},
        onShowTitleClicked = {},
        onShowUnitClicked = {},
        onAcceptClicked = {}
    )
}
