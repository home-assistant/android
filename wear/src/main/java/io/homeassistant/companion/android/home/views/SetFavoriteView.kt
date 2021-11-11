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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextOverflow
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
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.getIcon

@ExperimentalWearMaterialApi
@Composable
fun SetFavoritesView(
    validEntities: List<Entity<*>>,
    favoriteEntityIds: List<String>,
    onFavoriteSelected: (entityId: String, isSelected: Boolean) -> Unit
) {
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
            items(validEntities.size) { index ->
                val attributes = validEntities[index].attributes as Map<*, *>
                val iconBitmap = getIcon(
                    attributes["icon"] as String?,
                    validEntities[index].entityId.split(".")[0],
                    LocalContext.current
                )
                if (index == 0)
                    ListHeader(R.string.set_favorite)

                val entityId = validEntities[index].entityId
                val checked = favoriteEntityIds.contains(entityId)
                ToggleChip(
                    checked = checked,
                    onCheckedChange = {
                        onFavoriteSelected(entityId, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 40.dp else 10.dp),
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
        }
    }
}
