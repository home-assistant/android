package io.homeassistant.companion.android.home.views

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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.SetTitle
import io.homeassistant.companion.android.util.getIcon

@Composable
fun SetTileShortcutsView(
    shortcutEntities: MutableList<String>,
    onShortcutEntitySelectionChange: (Int) -> Unit
) {

    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    RotaryEventState(scrollState = scalingLazyListState)
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            top = 40.dp,
            start = 10.dp,
            end = 10.dp,
            bottom = 40.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = scalingLazyListState
    ) {
        item {
            SetTitle(id = R.string.shortcuts)
        }
        items(shortcutEntities.size) { index ->
            val favoriteEntityID = shortcutEntities[index].split(",")[0]
            val favoriteName = shortcutEntities[index].split(",")[1]
            val favoriteIcon = shortcutEntities[index].split(",")[2]

            val iconBitmap = getIcon(
                favoriteIcon,
                favoriteEntityID.split(".")[0],
                LocalContext.current
            )

            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                icon = {
                    Image(
                        iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone,
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.shortcut_n, index + 1)
                    )
                },
                secondaryLabel = {
                    Text(
                        text = favoriteName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                onClick = { onShortcutEntitySelectionChange(index) },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        if (shortcutEntities.size < 7) {
            item {
                Button(
                    modifier = Modifier
                        .padding(top = 4.dp),
                    onClick = { onShortcutEntitySelectionChange(shortcutEntities.size) },
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Image(
                        CommunityMaterial.Icon3.cmd_plus_thick
                    )
                }
            }
        }
    }
}
