package io.homeassistant.companion.android.home.views

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.onEntityClickedFeedback
import io.homeassistant.companion.android.common.R as commonR

@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Composable
fun MainView(
    mainViewModel: MainViewModel,
    favoriteEntityIds: List<String>,
    onEntityClicked: (String, String) -> Unit,
    onSettingsClicked: () -> Unit,
    onTestClicked: (entityLists: Map<Int, List<Entity<*>>>) -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean,
    deleteFavorite: (String) -> Unit
) {
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()

    var expandedFavorites: Boolean by rememberSaveable { mutableStateOf(true) }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    RotaryEventDispatcher(scalingLazyListState)
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                state = scalingLazyListState
            ) {
                if (favoriteEntityIds.isNotEmpty()) {
                    item {
                        ListHeader(
                            stringId = commonR.string.favorites,
                            expanded = expandedFavorites,
                            onExpandChanged = { expandedFavorites = it }
                        )
                    }
                    if (expandedFavorites) {
                        items(favoriteEntityIds.size) { index ->
                            val favoriteEntityID = favoriteEntityIds[index].split(",")[0]
                            if (mainViewModel.entities.isNullOrEmpty()) {
                                // Use a normal chip when we don't have the state of the entity
                                Chip(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    icon = {
                                        Image(
                                            asset = CommunityMaterial.Icon.cmd_cellphone,
                                            colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = favoriteEntityID,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        onEntityClicked(favoriteEntityID, "unknown")
                                        onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, favoriteEntityID, haptic)
                                    },
                                    colors = ChipDefaults.secondaryChipColors()
                                )
                            } else {
                                var isValidEntity = false
                                for (entity in mainViewModel.entities) {
                                    if (entity.value.entityId == favoriteEntityID) {
                                        isValidEntity = true
                                        EntityUi(
                                            mainViewModel.entities[favoriteEntityID]!!,
                                            onEntityClicked,
                                            isHapticEnabled,
                                            isToastEnabled
                                        )
                                    }
                                }
                                if (!isValidEntity) {
                                    deleteFavorite(favoriteEntityID)
                                }
                            }
                        }
                    }
                }
                if (mainViewModel.entities.isNullOrEmpty()) {
                    item {
                        Column {
                            ListHeader(id = commonR.string.loading)
                            Chip(
                                label = {
                                    Text(
                                        text = stringResource(commonR.string.loading_entities),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                onClick = { /* No op */ },
                                colors = ChipDefaults.primaryChipColors()
                            )
                        }
                    }
                } else {
                    item {
                        ListHeader(id = commonR.string.more_entities)
                    }
                    item {
                        Chip(
                            modifier = Modifier
                                .fillMaxWidth(),
                            icon = {
                                Image(
                                    asset = CommunityMaterial.Icon.cmd_animation
                                )
                            },
                            label = {
                                Text(text = stringResource(commonR.string.all_entities))
                            },
                            onClick = {
                                onTestClicked(
                                    mapOf(
                                        commonR.string.scenes to mainViewModel.scenes,
                                        commonR.string.input_booleans to mainViewModel.inputBooleans,
                                        commonR.string.lights to mainViewModel.lights,
                                        commonR.string.locks to mainViewModel.locks,
                                        commonR.string.scripts to mainViewModel.scripts,
                                        commonR.string.switches to mainViewModel.switches
                                    )
                                )
                            },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }

                // Buttons for each existing category
                if (mainViewModel.inputBooleans.isNotEmpty()) {
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                getIcon("", "input_boolean", context)?.let { Image(asset = it) }
                            },
                            label = {
                                Text(text = stringResource(commonR.string.input_booleans))
                            },
                            onClick = {
                                onTestClicked(
                                    mapOf(
                                        commonR.string.input_booleans to mainViewModel.inputBooleans
                                    )
                                )
                            },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
                if (mainViewModel.lights.isNotEmpty()) {
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                getIcon("", "light", context)?.let { Image(asset = it) }
                            },
                            label = {
                                Text(text = stringResource(commonR.string.lights))
                            },
                            onClick = {
                                onTestClicked(
                                    mapOf(
                                        commonR.string.lights to mainViewModel.lights
                                    )
                                )
                            },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
                if (mainViewModel.locks.isNotEmpty()) {
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                getIcon("", "lock", context)?.let { Image(asset = it) }
                            },
                            label = {
                                Text(text = stringResource(commonR.string.locks))
                            },
                            onClick = {
                                onTestClicked(
                                    mapOf(
                                        commonR.string.locks to mainViewModel.locks
                                    )
                                )
                            },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
                if (mainViewModel.scenes.isNotEmpty()) {
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                getIcon("", "scene", context)?.let { Image(asset = it) }
                            },
                            label = {
                                Text(text = stringResource(commonR.string.scenes))
                            },
                            onClick = {
                                onTestClicked(
                                    mapOf(
                                        commonR.string.scenes to mainViewModel.scenes
                                    )
                                )
                            },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
                if (mainViewModel.scripts.isNotEmpty()) {
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                getIcon("", "script", context)?.let { Image(asset = it) }
                            },
                            label = {
                                Text(text = stringResource(commonR.string.scripts))
                            },
                            onClick = {
                                onTestClicked(
                                    mapOf(
                                        commonR.string.scripts to mainViewModel.scripts
                                    )
                                )
                            },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
                if (mainViewModel.switches.isNotEmpty()) {
                    item {
                        Chip(
                            modifier = Modifier.fillMaxWidth(),
                            icon = {
                                getIcon("", "switch", context)?.let { Image(asset = it) }
                            },
                            label = {
                                Text(text = stringResource(commonR.string.switches))
                            },
                            onClick = {
                                onTestClicked(
                                    mapOf(
                                        commonR.string.switches to mainViewModel.switches
                                    )
                                )
                            },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }

                // Settings
                item {
                    Chip(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        icon = {
                            Image(
                                asset = CommunityMaterial.Icon.cmd_cog,
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(id = commonR.string.settings)
                            )
                        },
                        onClick = onSettingsClicked,
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }
    }
}
