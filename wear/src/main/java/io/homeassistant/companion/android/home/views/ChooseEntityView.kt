package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.SetTitle
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.setChipDefaults

@Composable
fun ChooseEntityView(
    validEntities: List<Entity<*>>,
    onNoneClicked: () -> Unit,
    onEntitySelected: (entityId: String) -> Unit
) {
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    RotaryEventState(scrollState = scalingLazyListState)
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
            SetTitle(id = R.string.shortcuts)
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp),
                icon = { Image(asset = CommunityMaterial.Icon.cmd_delete) },
                label = { Text(text = "None") },
                onClick = onNoneClicked,
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                )
            )
        }
        items(validEntities.size) { index ->
            val attributes = validEntities[index].attributes as Map<*, *>
            val iconBitmap = getIcon(
                attributes["icon"] as String?,
                validEntities[index].entityId.split(".")[0],
                LocalContext.current)
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 30.dp else 10.dp),
                icon = { Image(asset = iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone) },
                label = {
                    Text(
                        text = attributes["friendly_name"].toString(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                enabled = validEntities[index].state != "unavailable",
                onClick = {
                    val elementString = "${validEntities[index].entityId},${attributes["friendly_name"]},${attributes["icon"]}"
                    onEntitySelected(elementString)
                },
                colors = setChipDefaults()
            )
        }
    }
}