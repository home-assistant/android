package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.theme.wearColorPalette
import io.homeassistant.companion.android.util.IntervalToString
import io.homeassistant.companion.android.util.scrollHandler

@ExperimentalComposeUiApi
@Composable
fun TemplateTileSettingsView(
    templateContent: String,
    refreshInterval: Int,
    onClickRefreshInterval: () -> Unit
) {
    val scalingLazyListState: ScalingLazyListState = rememberScalingLazyListState()
    LocalView.current.requestFocus()

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
            ListHeader(id = R.string.template_tile)
        }
        item {
            Chip(
                modifier = Modifier
                    .fillMaxWidth(),
                icon = {
                    Image(
                        asset = CommunityMaterial.Icon3.cmd_timer_cog,
                        colorFilter = ColorFilter.tint(wearColorPalette.onSurface)
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                label = {
                    Text(
                        text = stringResource(id = R.string.refresh_interval)
                    )
                },
                secondaryLabel = { Text(IntervalToString(LocalContext.current, refreshInterval)) },
                onClick = onClickRefreshInterval
            )
        }
        item {
            ListHeader(R.string.template_tile_content)
        }
        item {
            Text(stringResource(R.string.template_tile_change_message))
        }
        item {
            Text(
                templateContent,
                color = Color.DarkGray
            )
        }
    }
}
