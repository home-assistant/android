package io.homeassistant.companion.android.home.views

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.home.HomePresenterImpl
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventHandlerSetup
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.previewFavoritesList
import io.homeassistant.companion.android.common.R as commonR

@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Composable
fun SetFavoritesView(
    mainViewModel: MainViewModel,
    favoriteEntityIds: List<String>,
    onFavoriteSelected: (entityId: String, isSelected: Boolean) -> Unit
) {
    var expandedInputBooleans: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedLights: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedLocks: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedScenes: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedScripts: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedSwitches: Boolean by rememberSaveable { mutableStateOf(true) }

    val validEntities = mainViewModel.entities
        .filter { it.key.split(".")[0] in HomePresenterImpl.supportedDomains }
    val validEntityList = validEntities.values.toList().sortedBy { it.entityId }

    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    RotaryEventState(scrollState = scalingLazyListState)

    WearAppTheme {
        Scaffold(
            positionIndicator = {
                if (scalingLazyListState.isScrollInProgress)
                    PositionIndicator(scalingLazyListState = scalingLazyListState)
            },
            timeText = { TimeText(!scalingLazyListState.isScrollInProgress) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 24.dp,
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 48.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                state = scalingLazyListState
            ) {
                item {
                    ListHeader(id = commonR.string.set_favorite)
                }
                if (favoriteEntityIds.isNotEmpty()) {
                    val favoriteEntities = mutableListOf<Entity<*>>()
                    for (entity in validEntityList) {
                        if (favoriteEntityIds.contains(entity.entityId))
                            favoriteEntities += listOf(entity)
                    }
                    items(favoriteEntities.size) { index ->
                        FavoriteToggleChip(
                            entityList = favoriteEntities,
                            index = index,
                            favoriteEntityIds = favoriteEntityIds,
                            onFavoriteSelected = onFavoriteSelected
                        )
                    }
                }
                if (mainViewModel.inputBooleans.isNotEmpty()) {
                    item {
                        ListHeader(
                            stringId = commonR.string.input_booleans,
                            expanded = expandedInputBooleans,
                            onExpandChanged = { expandedInputBooleans = it }
                        )
                    }
                    if (expandedInputBooleans) {
                        items(mainViewModel.inputBooleans.size) { index ->
                            FavoriteToggleChip(
                                entityList = mainViewModel.inputBooleans,
                                index = index,
                                favoriteEntityIds = favoriteEntityIds,
                                onFavoriteSelected = onFavoriteSelected
                            )
                        }
                    }
                }

                if (mainViewModel.lights.isNotEmpty()) {
                    item {
                        ListHeader(
                            stringId = commonR.string.lights,
                            expanded = expandedLights,
                            onExpandChanged = { expandedLights = it }
                        )
                    }
                    if (expandedLights) {
                        items(mainViewModel.lights.size) { index ->
                            FavoriteToggleChip(
                                entityList = mainViewModel.lights,
                                index = index,
                                favoriteEntityIds = favoriteEntityIds,
                                onFavoriteSelected = onFavoriteSelected
                            )
                        }
                    }
                }

                if (mainViewModel.locks.isNotEmpty()) {
                    item {
                        ListHeader(
                            stringId = commonR.string.locks,
                            expanded = expandedLocks,
                            onExpandChanged = { expandedLocks = it }
                        )
                    }
                    if (expandedLocks) {
                        items(mainViewModel.locks.size) { index ->
                            FavoriteToggleChip(
                                entityList = mainViewModel.locks,
                                index = index,
                                favoriteEntityIds = favoriteEntityIds,
                                onFavoriteSelected = onFavoriteSelected
                            )
                        }
                    }
                }

                if (mainViewModel.scenes.isNotEmpty()) {
                    item {
                        ListHeader(
                            stringId = commonR.string.scenes,
                            expanded = expandedScenes,
                            onExpandChanged = { expandedScenes = it }
                        )
                    }
                    if (expandedScenes) {
                        items(mainViewModel.scenes.size) { index ->
                            FavoriteToggleChip(
                                entityList = mainViewModel.scenes,
                                index = index,
                                favoriteEntityIds = favoriteEntityIds,
                                onFavoriteSelected = onFavoriteSelected
                            )
                        }
                    }
                }

                if (mainViewModel.scripts.isNotEmpty()) {
                    item {
                        ListHeader(
                            stringId = commonR.string.scripts,
                            expanded = expandedScripts,
                            onExpandChanged = { expandedScripts = it }
                        )
                    }
                    if (expandedScripts) {
                        items(mainViewModel.scripts.size) { index ->
                            FavoriteToggleChip(
                                entityList = mainViewModel.scripts,
                                index = index,
                                favoriteEntityIds = favoriteEntityIds,
                                onFavoriteSelected = onFavoriteSelected
                            )
                        }
                    }
                }

                if (mainViewModel.switches.isNotEmpty()) {
                    item {
                        ListHeader(
                            stringId = commonR.string.switches,
                            expanded = expandedSwitches,
                            onExpandChanged = { expandedSwitches = it }
                        )
                    }
                    if (expandedSwitches) {
                        items(mainViewModel.switches.size) { index ->
                            FavoriteToggleChip(
                                entityList = mainViewModel.switches,
                                index = index,
                                favoriteEntityIds = favoriteEntityIds,
                                onFavoriteSelected = onFavoriteSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteToggleChip(
    entityList: List<Entity<*>>,
    index: Int,
    favoriteEntityIds: List<String>,
    onFavoriteSelected: (entityId: String, isSelected: Boolean) -> Unit
) {
    val attributes = entityList[index].attributes as Map<*, *>
    val iconBitmap = getIcon(
        attributes["icon"] as String?,
        entityList[index].entityId.split(".")[0],
        LocalContext.current
    )

    val entityId = entityList[index].entityId
    val checked = favoriteEntityIds.contains(entityId)
    ToggleChip(
        checked = checked,
        onCheckedChange = {
            onFavoriteSelected(entityId, it)
        },
        modifier = Modifier
            .fillMaxWidth(),
        appIcon = {
            Image(
                asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone,
                colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
            )
        },
        label = {
            Text(
                text = attributes["friendly_name"].toString(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        toggleIcon = { ToggleChipDefaults.SwitchIcon(checked) }
    )
}

@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Preview
@Composable
private fun PreviewSetFavoriteView() {
    val rotaryEventDispatcher = RotaryEventDispatcher()
    CompositionLocalProvider(
        LocalRotaryEventDispatcher provides rotaryEventDispatcher
    ) {
        RotaryEventHandlerSetup(rotaryEventDispatcher)
        SetFavoritesView(
            mainViewModel = MainViewModel(),
            favoriteEntityIds = previewFavoritesList,
            onFavoriteSelected = { _, _ -> }
        )
    }
}
