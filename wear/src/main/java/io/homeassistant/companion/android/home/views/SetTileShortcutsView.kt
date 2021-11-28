package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import io.homeassistant.companion.android.data.SimplifiedEntity
import io.homeassistant.companion.android.util.LocalRotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventDispatcher
import io.homeassistant.companion.android.util.RotaryEventHandlerSetup
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.getIcon
import io.homeassistant.companion.android.util.simplifiedEntity
import io.homeassistant.companion.android.common.R as commonR

@Composable
fun SetTileShortcutsView(
    shortcutEntities: MutableList<SimplifiedEntity>,
    onShortcutEntitySelectionChange: (Int) -> Unit
) {

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
            ListHeader(id = commonR.string.shortcuts)
        }
        items(shortcutEntities.size) { index ->

            val iconBitmap = getIcon(
                shortcutEntities[index].icon,
                shortcutEntities[index].entityId.split(".")[0],
                LocalContext.current
            )

            Chip(
                modifier = Modifier
                    .fillMaxWidth(),
                icon = {
                    Image(
                        iconBitmap ?: CommunityMaterial.Icon.cmd_cellphone,
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                },
                label = {
                    Text(
                        text = stringResource(commonR.string.shortcut_n, index + 1)
                    )
                },
                secondaryLabel = {
                    Text(
                        text = shortcutEntities[index].friendlyName,
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
                    modifier = Modifier.padding(top = 16.dp),
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

@Preview
@Composable
private fun PreviewSetTileShortcutsView() {
    val rotaryEventDispatcher = RotaryEventDispatcher()
    CompositionLocalProvider(
        LocalRotaryEventDispatcher provides rotaryEventDispatcher
    ) {
        RotaryEventHandlerSetup(rotaryEventDispatcher)
        SetTileShortcutsView(
            shortcutEntities = mutableListOf(simplifiedEntity),
            onShortcutEntitySelectionChange = {}
        )
    }
}
