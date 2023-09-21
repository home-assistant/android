package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.data.items
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.onEntityClickedFeedback
import io.homeassistant.companion.android.views.ExpandableListHeader
import io.homeassistant.companion.android.views.ListHeader
import io.homeassistant.companion.android.views.ThemeLazyColumn
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun MainView(
    mainViewModel: MainViewModel,
    favoriteEntityIds: List<String>,
    onEntityClicked: (String, String) -> Unit,
    onEntityLongClicked: (String) -> Unit,
    onRetryLoadEntitiesClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onNavigationClicked: (entityLists: Map<String, List<Entity<*>>>, listOrder: List<String>, filter: (Entity<*>) -> Boolean) -> Unit,
    isHapticEnabled: Boolean,
    isToastEnabled: Boolean
) {
    val scalingLazyListState = rememberScalingLazyListState()

    var expandedFavorites: Boolean by rememberSaveable { mutableStateOf(true) }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    WearAppTheme {
        Scaffold(
            positionIndicator = {
                if (scalingLazyListState.isScrollInProgress) {
                    PositionIndicator(scalingLazyListState = scalingLazyListState)
                }
            },
            timeText = { TimeText(scalingLazyListState = scalingLazyListState) }
        ) {
            ThemeLazyColumn(
                state = scalingLazyListState
            ) {
                if (favoriteEntityIds.isNotEmpty()) {
                    item {
                        ExpandableListHeader(
                            string = stringResource(commonR.string.favorites),
                            expanded = expandedFavorites,
                            onExpandChanged = { expandedFavorites = it }
                        )
                    }
                    if (expandedFavorites) {
                        items(favoriteEntityIds, key = { "favorite-${it}" }) { id ->
                            val favoriteEntityID = id.split(",")[0]
                            if (mainViewModel.entities.isEmpty()) {
                                // when we don't have the state of the entity, create a Chip from cache as we don't have the state yet
                                val cached = mainViewModel.getCachedEntity(favoriteEntityID)
                                Chip(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    icon = {
                                        Image(
                                            asset = getIcon(cached?.icon, favoriteEntityID.split(".")[0], context),
                                            colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = cached?.friendlyName ?: favoriteEntityID,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    onClick = {
                                        onEntityClicked(favoriteEntityID, STATE_UNKNOWN)
                                        onEntityClickedFeedback(isToastEnabled, isHapticEnabled, context, favoriteEntityID, haptic)
                                    },
                                    colors = ChipDefaults.secondaryChipColors()
                                )
                            } else {
                                mainViewModel.entities.values.toList()
                                    .firstOrNull { it.entityId == favoriteEntityID }
                                    ?.let {
                                        EntityUi(
                                            mainViewModel.entities[favoriteEntityID]!!,
                                            onEntityClicked,
                                            isHapticEnabled,
                                            isToastEnabled
                                        ) { entityId -> onEntityLongClicked(entityId) }
                                    }
                            }
                        }
                    }
                }

                if (!mainViewModel.isFavoritesOnly) {
                    when (mainViewModel.loadingState.value) {
                        MainViewModel.LoadingState.LOADING -> {
                            if (favoriteEntityIds.isEmpty()) {
                                // Add a Spacer to prevent settings being pushed to the screen center
                                item { Spacer(modifier = Modifier.fillMaxWidth()) }
                            }
                            item {
                                val minHeight =
                                    if (favoriteEntityIds.isEmpty()) {
                                        LocalConfiguration.current.screenHeightDp - 64
                                    } else {
                                        0
                                    }
                                Column(
                                    modifier = Modifier
                                        .heightIn(min = minHeight.dp)
                                        .fillMaxSize()
                                        .padding(vertical = if (favoriteEntityIds.isEmpty()) 0.dp else 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    ListHeader(id = commonR.string.loading)
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        MainViewModel.LoadingState.ERROR -> {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    ListHeader(id = commonR.string.error_loading_entities)
                                    Chip(
                                        label = {
                                            Text(
                                                text = stringResource(commonR.string.retry),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        },
                                        onClick = onRetryLoadEntitiesClicked,
                                        colors = ChipDefaults.primaryChipColors()
                                    )
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                            }
                        }
                        MainViewModel.LoadingState.READY -> {
                            if (mainViewModel.entities.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = stringResource(commonR.string.no_supported_entities),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.title3,
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(top = 32.dp)
                                        )
                                        Text(
                                            text = stringResource(commonR.string.no_supported_entities_summary),
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.body2,
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(top = 8.dp)
                                        )
                                    }
                                }
                            }

                            if (
                                mainViewModel.entitiesByArea.values.any {
                                    it.isNotEmpty() && it.any { entity ->
                                        mainViewModel.getCategoryForEntity(entity.entityId) == null &&
                                            mainViewModel.getHiddenByForEntity(entity.entityId) == null
                                    }
                                }
                            ) {
                                item {
                                    ListHeader(id = commonR.string.areas)
                                }
                                items(mainViewModel.entitiesByArea, key = { "area-${it}" }) { (id, entities) ->
                                    val entitiesToShow = entities.filter {
                                        mainViewModel.getCategoryForEntity(it.entityId) == null &&
                                                mainViewModel.getHiddenByForEntity(it.entityId) == null
                                    }
                                    if (entitiesToShow.isNotEmpty()) {
                                        val area = mainViewModel.areas.first { it.areaId == id }
                                        Chip(
                                            modifier = Modifier.fillMaxWidth(),
                                            label = {
                                                Text(text = area.name)
                                            },
                                            onClick = {
                                                onNavigationClicked(
                                                    mapOf(area.name to entities),
                                                    listOf(area.name)
                                                ) {
                                                    mainViewModel.getCategoryForEntity(it.entityId) == null &&
                                                            mainViewModel.getHiddenByForEntity(
                                                                it.entityId
                                                            ) == null
                                                }
                                            },
                                            colors = ChipDefaults.primaryChipColors()
                                        )
                                    }
                                }
                            }

                            val domainEntitiesFilter: (entity: Entity<*>) -> Boolean =
                                {
                                    mainViewModel.getAreaForEntity(it.entityId) == null &&
                                        mainViewModel.getCategoryForEntity(it.entityId) == null &&
                                        mainViewModel.getHiddenByForEntity(it.entityId) == null
                                }
                            if (mainViewModel.entities.values.any(domainEntitiesFilter)) {
                                item {
                                    ListHeader(id = commonR.string.more_entities)
                                }
                            }
                            // Buttons for each existing category
                            items(mainViewModel.entitiesByDomain, key = { "domain-${it}" }) { (domain, domainEntities) ->
                                val domainEntitiesToShow = domainEntities.filter(domainEntitiesFilter)
                                if (domainEntitiesToShow.isNotEmpty()) {
                                    Chip(
                                        modifier = Modifier.fillMaxWidth(),
                                        icon = {
                                            getIcon(
                                                "",
                                                domain,
                                                context
                                            ).let { Image(asset = it) }
                                        },
                                        label = {
                                            Text(text = mainViewModel.stringForDomain(domain)!!)
                                        },
                                        onClick = {
                                            onNavigationClicked(
                                                mapOf(
                                                    mainViewModel.stringForDomain(domain)!! to domainEntities
                                                ),
                                                listOf(mainViewModel.stringForDomain(domain)!!),
                                                domainEntitiesFilter
                                            )
                                        },
                                        colors = ChipDefaults.primaryChipColors()
                                    )
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
                                            onNavigationClicked(
                                                mainViewModel.entitiesByDomain.mapKeys {
                                                    mainViewModel.stringForDomain(
                                                        it.key
                                                    )!!
                                                },
                                                mainViewModel.entitiesByDomain.keys.map {
                                                    mainViewModel.stringForDomain(
                                                        it
                                                    )!!
                                                }.sorted()
                                            ) { true }
                                        },
                                        colors = ChipDefaults.secondaryChipColors()
                                    )
                                }
                            }
                        }
                    }
                }

                if (mainViewModel.isFavoritesOnly) {
                    item {
                        Spacer(Modifier.padding(32.dp))
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
