package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.getIcon

@Composable
fun ChooseEntityView(
    validEntities: Map<String, Entity<*>>,
    onNoneClicked: () -> Unit,
    onEntitySelected: (entity: SimplifiedEntity) -> Unit
) {
    val validEntityList = validEntities.values.toList()
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    RotaryEventState(scrollState = scalingLazyListState)
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            top = 40.dp,
            start = 8.dp,
            end = 8.dp,
            bottom = 40.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = scalingLazyListState
    ) {
        item {
            ListHeader(id = R.string.shortcuts)
        }
        item {
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                icon = { Image(asset = CommunityMaterial.Icon.cmd_delete) },
                label = { Text(stringResource(id = R.string.none)) },
                onClick = onNoneClicked,
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                )
            )
        }
        items(validEntityList.size) { index ->
            val attributes = validEntityList[index].attributes as Map<*, *>
            val iconBitmap = getIcon(
                attributes["icon"] as String?,
                validEntityList[index].entityId.split(".")[0],
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
                enabled = validEntityList[index].state != "unavailable",
                onClick = {
                    onEntitySelected(
                        SimplifiedEntity(
                            validEntityList[index].entityId,
                            attributes["friendly_name"] as String? ?: validEntityList[index].entityId,
                            attributes["icon"] as String? ?: ""
                        )
                    )
                },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}
