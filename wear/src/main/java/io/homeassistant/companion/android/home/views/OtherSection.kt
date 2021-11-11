package io.homeassistant.companion.android.home.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R

@Composable
fun OtherSection(
    onSettingsClicked: () -> Unit,
    onLogoutClicked: () -> Unit
) {
    Column {
        ListHeader(id = R.string.other)
        Chip(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            icon = {
                Image(asset = CommunityMaterial.Icon.cmd_cog)
            },
            label = {
                Text(
                    text = stringResource(id = R.string.settings)
                )
            },
            onClick = onSettingsClicked,
            colors = ChipDefaults.primaryChipColors(
                contentColor = Color.Black
            )
        )
        Chip(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            icon = {
                Image(asset = CommunityMaterial.Icon.cmd_exit_run)
            },
            label = {
                Text(
                    text = stringResource(id = R.string.logout)
                )
            },
            onClick = onLogoutClicked,
            colors = ChipDefaults.primaryChipColors(
                backgroundColor = Color.Red,
                contentColor = Color.Black
            )
        )
    }
}
