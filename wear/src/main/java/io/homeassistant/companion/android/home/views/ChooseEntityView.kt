package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun ChooseEntityView(
    mainViewModel: MainViewModel,
    onNoneClicked: () -> Unit,
    onEntitySelected: (entity: SimplifiedEntity) -> Unit
) {
    var expandedInputBooleans: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedLights: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedLocks: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedScenes: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedScripts: Boolean by rememberSaveable { mutableStateOf(true) }
    var expandedSwitches: Boolean by rememberSaveable { mutableStateOf(true) }

    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    RotaryEventState(scrollState = scalingLazyListState)

    WearAppTheme {
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
            item {
                ListHeader(id = commonR.string.shortcuts)
            }
            item {
                Chip(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    icon = { Image(asset = CommunityMaterial.Icon.cmd_delete) },
                    label = { Text(stringResource(id = commonR.string.none)) },
                    onClick = onNoneClicked,
                    colors = ChipDefaults.primaryChipColors(
                        contentColor = Color.Black
                    )
                )
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
                        ChooseEntityChip(
                            entityList = mainViewModel.inputBooleans,
                            index = index,
                            onEntitySelected = onEntitySelected
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
                        ChooseEntityChip(
                            entityList = mainViewModel.locks,
                            index = index,
                            onEntitySelected = onEntitySelected
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
                        ChooseEntityChip(
                            entityList = mainViewModel.lights,
                            index = index,
                            onEntitySelected = onEntitySelected
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
                        ChooseEntityChip(
                            entityList = mainViewModel.scenes,
                            index = index,
                            onEntitySelected = onEntitySelected
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
                        ChooseEntityChip(
                            entityList = mainViewModel.scripts,
                            index = index,
                            onEntitySelected = onEntitySelected
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
                        ChooseEntityChip(
                            entityList = mainViewModel.switches,
                            index = index,
                            onEntitySelected = onEntitySelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseEntityChip(
    entityList: List<Entity<*>>,
    index: Int,
    onEntitySelected: (entity: SimplifiedEntity) -> Unit
) {
    val attributes = entityList[index].attributes as Map<*, *>
    val iconBitmap = getIcon(
        attributes["icon"] as String?,
        entityList[index].entityId.split(".")[0],
        LocalContext.current
    )
    Chip(
        modifier = Modifier
            .fillMaxWidth(),
        icon = {
            Image(
                asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone,
                colorFilter = ColorFilter.tint(Color.White)
            )
        },
        label = {
            Text(
                text = attributes["friendly_name"].toString(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        enabled = entityList[index].state != "unavailable",
        onClick = {
            onEntitySelected(
                SimplifiedEntity(
                    entityList[index].entityId,
                    attributes["friendly_name"] as String? ?: entityList[index].entityId,
                    attributes["icon"] as String? ?: ""
                )
            )
        },
        colors = ChipDefaults.secondaryChipColors()
    )
}
