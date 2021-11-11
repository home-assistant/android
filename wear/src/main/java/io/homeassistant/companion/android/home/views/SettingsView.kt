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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import io.homeassistant.companion.android.util.RotaryEventState
import io.homeassistant.companion.android.util.previewFavoritesList

@Composable
fun SettingsView(
    favorites: List<String>,
    onClickSetFavorites: () -> Unit,
    onClearFavorites: () -> Unit,
    onClickSetShortcuts: () -> Unit
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
            ListHeader(id = R.string.settings)
        }
        item {
            Chip(
                modifier = Modifier
                    .fillMaxWidth(),
                icon = {
                    Image(asset = CommunityMaterial.Icon3.cmd_star)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.favorite)
                    )
                },
                onClick = onClickSetFavorites,
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                )
            )
        }
        item {
            Chip(
                modifier = Modifier
                    .fillMaxWidth(),
                icon = {
                    Image(asset = CommunityMaterial.Icon.cmd_delete)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.clear_favorites),
                    )
                },
                onClick = onClearFavorites,
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                ),
                secondaryLabel = {
                    Text(
                        text = stringResource(id = R.string.irreverisble)
                    )
                },
                enabled = favorites.isNotEmpty()
            )
        }

        item {
            ListHeader(
                id = R.string.tile_settings,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        item {
            Chip(
                modifier = Modifier
                    .fillMaxWidth(),
                icon = {
                    Image(asset = CommunityMaterial.Icon3.cmd_star_circle_outline)
                },
                label = {
                    Text(
                        text = stringResource(id = R.string.shortcuts)
                    )
                },
                onClick = onClickSetShortcuts,
                colors = ChipDefaults.primaryChipColors(
                    contentColor = Color.Black
                )
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSettingsView() {
    SettingsView(
        favorites = previewFavoritesList,
        onClickSetFavorites = { /*TODO*/ },
        onClearFavorites = {},
        onClickSetShortcuts = {}
    )
}
