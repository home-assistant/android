package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.home.MainViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.common.R as commonR

@ExperimentalComposeUiApi
@Composable
fun ChooseEntityView(
    mainViewModel: MainViewModel,
    onNoneClicked: () -> Unit,
    onEntitySelected: (entity: SimplifiedEntity) -> Unit
) {
    // Remember expanded state of each header
    val expandedStates = rememberExpandedStates(mainViewModel.supportedDomains())

    WearAppTheme {
        ThemeLazyColumn {
            item {
                ListHeader(id = commonR.string.shortcuts_choose)
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
                val entities = mainViewModel.entitiesByDomain[domain]
                if (!entities.isNullOrEmpty()) {
                    item {
                        ExpandableListHeader(
                            string = mainViewModel.stringForDomain(domain)!!,
                            key = domain,
                            expandedStates = expandedStates
                        )
                    }
                    if (expandedStates[domain] == true) {
                        items(entities.size) { index ->
                            ChooseEntityChip(
                                entityList = entities,
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
