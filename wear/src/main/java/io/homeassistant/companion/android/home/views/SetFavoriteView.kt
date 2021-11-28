package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventHandlerSetup
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.previewEntityList
import io.homeassistant.companion.android.util.previewFavoritesList
import io.homeassistant.companion.android.common.R as commonR

@ExperimentalWearMaterialApi
@Composable
fun SetFavoritesView(
    validEntities: Map<String, Entity<*>>,
    favoriteEntityIds: List<String>,
    onFavoriteSelected: (entityId: String, isSelected: Boolean) -> Unit
) {
    var expandedInputBooleans: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedLights: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedLocks: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedScenes: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedScripts: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedSwitches: Boolean by rememberSaveable { mutableStateOf(true) }

    val validEntityList = validEntities.values.toList().sortedBy { it.entityId }
    val scenes = validEntityList.filter { it.entityId.split(".")[0] == "scene" }
    val scripts = validEntityList.filter { it.entityId.split(".")[0] == "script" }
    val lights = validEntityList.filter { it.entityId.split(".")[0] == "light" }
    val locks = validEntityList.filter { it.entityId.split(".")[0] == "lock" }
    val inputBooleans = validEntityList.filter { it.entityId.split(".")[0] == "input_boolean" }
    val switches = validEntityList.filter { it.entityId.split(".")[0] == "switch" }

    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    RotaryEventState(scrollState = scalingLazyListState)
    Scaffold(
        positionIndicator = {
            if (scalingLazyListState.isScrollInProgress)
                PositionIndicator(scalingLazyListState = scalingLazyListState)
        },
        timeText = {
            if (!scalingLazyListState.isScrollInProgress)
                TimeText()
        }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                top = 10.dp,
                start = 10.dp,
                end = 10.dp,
                bottom = 40.dp
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
                items(favoriteEntityIds.size) { index ->
                    FavoriteToggleChip(
                        entityList = favoriteEntities,
                        index = index,
                        favoriteEntityIds = favoriteEntityIds,
                        onFavoriteSelected = onFavoriteSelected
                    )
                }
            }
            if (inputBooleans.isNotEmpty()) {
                item {
                    ListHeader(
                        stringId = commonR.string.input_booleans,
                        expanded = expandedInputBooleans,
                        onExpandChanged = { expandedInputBooleans = it }
                    )
                }
                if (expandedInputBooleans) {
                    items(inputBooleans.size) { index ->
                        FavoriteToggleChip(
                            entityList = inputBooleans,
                            index = index,
                            favoriteEntityIds = favoriteEntityIds,
                            onFavoriteSelected = onFavoriteSelected
                        )
                    }
                }
            }
            if (lights.isNotEmpty()) {
                item {
                    ListHeader(
                        stringId = commonR.string.lights,
                        expanded = expandedLights,
                        onExpandChanged = { expandedLights = it }
                    )
                }
                if (expandedLights) {
                    items(lights.size) { index ->
                        FavoriteToggleChip(
                            entityList = lights,
                            index = index,
                            favoriteEntityIds = favoriteEntityIds,
                            onFavoriteSelected = onFavoriteSelected
                        )
                    }
                }
            }
            if (locks.isNotEmpty()) {
                item {
                    ListHeader(
                        stringId = commonR.string.locks,
                        expanded = expandedLocks,
                        onExpandChanged = { expandedLocks = it }
                    )
                }
                if (expandedLocks) {
                    items(locks.size) { index ->
                        FavoriteToggleChip(
                            entityList = locks,
                            index = index,
                            favoriteEntityIds = favoriteEntityIds,
                            onFavoriteSelected = onFavoriteSelected
                        )
                    }
                }
            }
            if (scenes.isNotEmpty()) {
                item {
                    ListHeader(
                        stringId = commonR.string.scenes,
                        expanded = expandedScenes,
                        onExpandChanged = { expandedScenes = it }
                    )
                }
                if (expandedScenes) {
                    items(scenes.size) { index ->
                        FavoriteToggleChip(
                            entityList = scenes,
                            index = index,
                            favoriteEntityIds = favoriteEntityIds,
                            onFavoriteSelected = onFavoriteSelected
                        )
                    }
                }
            }
            if (scripts.isNotEmpty()) {
                item {
                    ListHeader(
                        stringId = commonR.string.scripts,
                        expanded = expandedScripts,
                        onExpandChanged = { expandedScripts = it }
                    )
                }
                if (expandedScripts) {
                    items(scripts.size) { index ->
                        FavoriteToggleChip(
                            entityList = scripts,
                            index = index,
                            favoriteEntityIds = favoriteEntityIds,
                            onFavoriteSelected = onFavoriteSelected
                        )
                    }
                }
            }
            if (switches.isNotEmpty()) {
                item {
                    ListHeader(
                        stringId = commonR.string.switches,
                        expanded = expandedSwitches,
                        onExpandChanged = { expandedSwitches = it }
                    )
                }
                if (expandedSwitches) {
                    items(switches.size) { index ->
                        FavoriteToggleChip(
                            entityList = switches,
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
            .fillMaxWidth()
            .padding(top = 10.dp),
        appIcon = { Image(asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone) },
        label = {
            Text(
                text = attributes["friendly_name"].toString(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        toggleIcon = { ToggleChipDefaults.SwitchIcon(checked) },
        colors = ToggleChipDefaults.toggleChipColors(
            checkedStartBackgroundColor = colorResource(id = R.color.colorAccent),
            checkedEndBackgroundColor = colorResource(id = R.color.colorAccent),
            uncheckedStartBackgroundColor = colorResource(id = R.color.colorAccent),
            uncheckedEndBackgroundColor = colorResource(id = R.color.colorAccent),
            checkedContentColor = Color.Black,
            uncheckedContentColor = Color.Black,
            checkedToggleIconTintColor = Color.Yellow,
            uncheckedToggleIconTintColor = Color.DarkGray
        )
    )
}

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
            validEntities = previewEntityList,
            favoriteEntityIds = previewFavoritesList,
            onFavoriteSelected = { _, _ -> }
        )
    }
}
