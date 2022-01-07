package io.homeassistant.companion.android.home.views

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
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
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.scrollHandler
import io.homeassistant.companion.android.common.R as commonR

@ExperimentalComposeUiApi
@ExperimentalAnimationApi
@ExperimentalWearMaterialApi
@Composable
fun SetFavoritesView(
    mainViewModel: MainViewModel,
    favoriteEntityIds: List<String>,
    onFavoriteSelected: (entityId: String, entityPosition: Int, isSelected: Boolean) -> Unit
) {
    // Remember expanded state of each header
    val expandedStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            mainViewModel.supportedDomains().forEach {
                put(it, true)
            }
        }
    }

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
                horizontalAlignment = Alignment.CenterHorizontally,
                state = scalingLazyListState
            ) {
                item {
                    ListHeader(id = commonR.string.set_favorite)
                }
                for (domain in mainViewModel.entitiesByDomainOrder) {
                    val entities = mainViewModel.entitiesByDomain[domain].orEmpty()
                    if (entities.isNotEmpty()) {
                        item {
                            ListHeader(
                                string = mainViewModel.stringForDomain(domain)!!,
                                expanded = expandedStates[domain]!!,
                                onExpandChanged = { expandedStates[domain] = it }
                            )
                        }
                        if (expandedStates[domain] == true) {
                            items(mainViewModel.entitiesByDomain[domain].orEmpty().size) { index ->
                                FavoriteToggleChip(
                                    entityList = mainViewModel.entitiesByDomain[domain]!!,
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
}

@Composable
private fun FavoriteToggleChip(
    entityList: List<Entity<*>>,
    index: Int,
    favoriteEntityIds: List<String>,
    onFavoriteSelected: (entityId: String, entityPosition: Int, isSelected: Boolean) -> Unit
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
            onFavoriteSelected(entityId, favoriteEntityIds.size + 1, it)
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
