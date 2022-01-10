package io.homeassistant.companion.android.settings.wear.views

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.util.IntervalToString

@Composable
fun SettingsWearTemplateTile(
    template: String,
    renderedTemplate: String,
    refreshInterval: Int,
    onContentChanged: (String) -> Unit,
    onRefreshIntervalChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.template_tile_content)) },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEAR_DOCS_LINK))
                        context.startActivity(intent)
                    }) {
                        Icon(
                            Icons.Filled.HelpOutline,
                            contentDescription = stringResource(id = R.string.help)
                        )
                    }
                }
            )
        }
    ) {
        Column(Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    asset = CommunityMaterial.Icon3.cmd_timer_cog,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier
                        .height(24.dp)
                        .width(24.dp)
                )
                Text(
                    stringResource(R.string.refresh_interval),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                )
                Box {
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { dropdownExpanded = true }
                    ) {
                        Text(IntervalToString(refreshInterval))
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        val options = listOf(0, 60, 2 * 60, 5 * 60, 10 * 60, 15 * 60, 30 * 60, 60 * 60, 5 * 60 * 60, 10 * 60 * 60, 24 * 60 * 60)
                        for (option in options) {
                            DropdownMenuItem(onClick = {
                                onRefreshIntervalChanged(option)
                                dropdownExpanded = false
                            }) {
                                Text(IntervalToString(option))
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.template_tile_help))
            TextField(
                value = template,
                onValueChange = onContentChanged,
                label = {
                    Text(stringResource(R.string.template_tile_content))
                },
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 10
            )
            Text(
                renderedTemplate,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
