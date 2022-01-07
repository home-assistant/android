package io.homeassistant.companion.android.home.views

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.onEntityClickedFeedback
import io.homeassistant.companion.android.util.scrollHandler
import io.homeassistant.companion.android.common.R as commonR

@ExperimentalComposeUiApi
@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Composable
fun MainView(
    mainViewModel: MainViewModel,
    favoriteEntityIds: List<String>,
    onEntityClicked: (String, String) -> Unit,
    onSettingsClicked: () -> Unit,
    onTestClicked: (entityLists: Map<String, List<Entity<*>>>, listOrder: List<String>, filter: (Entity<*>) -> (Boolean)) -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean,
    deleteFavorite: (String) -> Unit
) {
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()

    var expandedFavorites: Boolean by rememberSaveable { mutableStateOf(true) }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    LocalView.current.requestFocus()

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
                    .fillMaxSize()
                    .scrollHandler(scalingLazyListState),
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
                }

                if (mainViewModel.entitiesByArea.values.any { it.isNotEmpty() }) {
                    item {
                        ListHeader(id = commonR.string.areas)
                    }
                    for (id in mainViewModel.entitiesByAreaOrder) {
                        val entities = mainViewModel.entitiesByArea[id]!!
                        if (entities.isNotEmpty()) {
                            val area = mainViewModel.areas.first { it.areaId == id }
                            item {
                                Chip(
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text(text = area.name)
                                    },
                                    onClick = {
                                        onTestClicked(
                                            mapOf(area.name to mainViewModel.entitiesByArea[id]!!),
                                            listOf(area.name)
                                        ) { true }
                                    },
                                    colors = ChipDefaults.primaryChipColors()
                                )
                            }
                        }
                    }
                }

                if (mainViewModel.entities.values.any { mainViewModel.getAreaForEntity(it.entityId) == null }) {
                    item {
                        ListHeader(id = commonR.string.more_entities)
                    }
                }
                // Buttons for each existing category
                for (domain in mainViewModel.entitiesByDomainOrder) {
                    val domainEntitiesNotInArea = mainViewModel.entitiesByDomain[domain]!!.filter { mainViewModel.getAreaForEntity(it.entityId) == null }
                    if (domainEntitiesNotInArea.isNotEmpty()) {
                        item {
                            Chip(
                                modifier = Modifier.fillMaxWidth(),
                                icon = {
                                    getIcon("", domain, context)?.let { Image(asset = it) }
                                },
                                label = {
                                    Text(text = mainViewModel.stringForDomain(domain)!!)
                                },
                                onClick = {
                                    onTestClicked(
                                        mapOf(
                                            mainViewModel.stringForDomain(domain)!! to mainViewModel.entitiesByDomain[domain]!!
                                        ),
                                        listOf(mainViewModel.stringForDomain(domain)!!)
                                    ) { mainViewModel.getAreaForEntity(it.entityId) == null }
                                },
                                colors = ChipDefaults.primaryChipColors()
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
                // All entities regardless of area
                if (mainViewModel.entities.isNotEmpty()) {
                    item {
                        Chip(
                            modifier = Modifier
                                .fillMaxWidth(),
                            icon = {
                                Image(
                                    asset = CommunityMaterial.Icon.cmd_animation,
                                    colorFilter = ColorFilter.tint(Color.White)
                                )
                            },
                            label = {
                                Text(text = stringResource(commonR.string.all_entities))
                            },
                            onClick = {
                                onTestClicked(
                                    mainViewModel.entitiesByDomain.mapKeys { mainViewModel.stringForDomain(it.key)!! },
                                    mainViewModel.entitiesByDomain.keys.map { mainViewModel.stringForDomain(it)!! }.sorted()
                                ) { true }
                            },
                            colors = ChipDefaults.secondaryChipColors()
                        )
                    }
                }

                // Settings
                item {
                    Chip(
                        modifier = Modifier
                            .fillMaxWidth(),
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
