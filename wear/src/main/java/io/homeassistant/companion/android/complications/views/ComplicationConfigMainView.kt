package io.homeassistant.companion.android.complications.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ToggleButton
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.tooling.preview.devices.WearDevices
import com.mikepenz.iconics.compose.Image
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.complications.ComplicationConfigViewModel
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.getFilledTonalButtonColors
import io.homeassistant.companion.android.theme.getToggleButtonColors
import io.homeassistant.companion.android.theme.wearColorScheme
import io.homeassistant.companion.android.util.ToggleSwitch
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
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Image(
                            asset = iconBitmap,
                            colorFilter = ColorFilter.tint(wearColorScheme.onSurface)
                        )
                    },
                    colors = getFilledTonalButtonColors(),
                    label = { Text(stringResource(id = R.string.choose_entity)) },
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
                ToggleButton(
                    checked = isChecked,
                    onCheckedChange = onShowTitleClicked,
                    label = { Text(stringResource(R.string.show_entity_title)) },
                    toggleControl = { ToggleSwitch(isChecked) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loaded && entity != null,
                    colors = getToggleButtonColors()
                )
            }
            item {
                val isChecked = !loaded || showUnit
                ToggleButton(
                    checked = isChecked,
                    onCheckedChange = onShowUnitClicked,
                    label = { Text(stringResource(R.string.show_unit_title)) },
                    toggleControl = { ToggleSwitch(isChecked) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loaded && entity != null,
                    colors = getToggleButtonColors()
                )
            }

            item {
                FilledIconButton(
                    modifier = Modifier.padding(top = 8.dp).touchTargetAwareSize(IconButtonDefaults.SmallButtonSize),
                    onClick = { onAcceptClicked() },
                    enabled = loaded && entity != null
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(id = R.string.save),
                        modifier = Modifier.size(IconButtonDefaults.iconSizeFor(IconButtonDefaults.SmallButtonSize))
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

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
fun PreviewMainConfigView() {
    MainConfigView(
        entity = simplifiedEntity,
        showTitle = true,
        showUnit = false,
        loadingState = ComplicationConfigViewModel.LoadingState.READY,
        onChooseEntityClicked = {},
        onShowTitleClicked = {},
        onShowUnitClicked = {},
        onAcceptClicked = {}
    )
}
