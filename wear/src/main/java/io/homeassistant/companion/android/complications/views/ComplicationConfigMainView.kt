package io.homeassistant.companion.android.complications.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.HomeAssistantApplication
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.complications.ComplicationConfigViewModel
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.home.views.ChooseEntityView
import io.homeassistant.companion.android.home.views.ListHeader
import io.homeassistant.companion.android.home.views.ThemeColumn
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.getIcon

private const val SCREEN_MAIN = "main"
private const val SCREEN_CHOOSE_ENTITY = "choose_entity"

@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun LoadConfigView(
    complicationConfigViewModel: ComplicationConfigViewModel,
    onAcceptClicked: () -> Unit,
    onCancelClicked: () -> Unit
) {
    WearAppTheme {
        val swipeDismissableNavController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(
            navController = swipeDismissableNavController,
            startDestination = SCREEN_MAIN
        ) {
            composable(SCREEN_MAIN) {
                MainConfigView(
                    entity = complicationConfigViewModel.selectedEntity.value,
                    hasSelected = complicationConfigViewModel.hasSelected.value,
                    loaded = complicationConfigViewModel.loadingState.value == ComplicationConfigViewModel.LoadingState.READY,
                    error = complicationConfigViewModel.loadingState.value == ComplicationConfigViewModel.LoadingState.ERROR,
                    onChooseEntityClicked = {
                        swipeDismissableNavController.navigate(SCREEN_CHOOSE_ENTITY)
                    },
                    onAcceptClicked = onAcceptClicked,
                    onCancelClicked = onCancelClicked
                )
            }
            composable(SCREEN_CHOOSE_ENTITY) {
                val app = complicationConfigViewModel.getApplication<HomeAssistantApplication>()
                ChooseEntityView(app.applicationContext,
                    complicationConfigViewModel.entitiesByDomainOrder,
                    complicationConfigViewModel.entitiesByDomain,
                    {}, { entity ->
                        complicationConfigViewModel.setEntity(entity)
                        swipeDismissableNavController.navigateUp()
                    },
                    false
                )
            }
        }
    }
}

@Composable
fun MainConfigView(
    entity: SimplifiedEntity,
    hasSelected: Boolean,
    loaded: Boolean,
    error: Boolean,
    onChooseEntityClicked: () -> Unit,
    onAcceptClicked: () -> Unit,
    onCancelClicked: () -> Unit
) {
    ThemeColumn {
        ListHeader(id = R.string.complication_entity_state_label)
        if (!error) {
            val iconBitmap = getIcon(
                entity.icon,
                entity.domain,
                LocalContext.current
            )
            Chip(
                modifier = Modifier.fillMaxWidth(),
                icon = {
                    Image(
                        asset = iconBitmap ?: CommunityMaterial.Icon2.cmd_lightbulb,
                        colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                label = {
                    Text(
                        text = stringResource(id = R.string.choose_entity)
                    )
                },
                secondaryLabel = { Text(if (loaded) entity.friendlyName else stringResource(R.string.loading)) },
                enabled = loaded,
                onClick = onChooseEntityClicked
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = { onCancelClicked() },
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Image(
                        CommunityMaterial.Icon.cmd_close
                    )
                }
                Button(
                    onClick = { onAcceptClicked() },
                    colors = ButtonDefaults.primaryButtonColors(),
                    enabled = loaded && hasSelected
                ) {
                    Image(
                        CommunityMaterial.Icon.cmd_check
                    )
                }
            }
        } else {
            Text(text = stringResource(R.string.error_connection_failed))
        }
    }
}