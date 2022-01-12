package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.scrollHandler
import io.homeassistant.companion.android.common.R as commonR

@ExperimentalComposeUiApi
@Composable
fun ChooseEntityView(
    mainViewModel: MainViewModel,
    onNoneClicked: () -> Unit,
    onEntitySelected: (entity: SimplifiedEntity) -> Unit
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
                            ChooseEntityChip(
                                entityList = mainViewModel.entitiesByDomain[domain]!!,
                                index = index,
                                onEntitySelected = onEntitySelected
                            )
                        }
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
