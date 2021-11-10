package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.util.previewFavoritesList

@Composable
fun SettingsView(
    favorites: List<String>,
    onClickSetFavorites: () -> Unit,
    onClearFavorites: () -> Unit
) {
    Column {
        ListHeader(id = R.string.settings)
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
        Chip(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
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
}

@Preview
@Composable
private fun PreviewSettingsView() {
    SettingsView(
        favorites = previewFavoritesList,
        onClickSetFavorites = { /*TODO*/ },
        onClearFavorites = {}
    )
}
