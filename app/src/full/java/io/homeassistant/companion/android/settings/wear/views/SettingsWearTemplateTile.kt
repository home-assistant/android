package io.homeassistant.companion.android.settings.wear.views

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R

@Composable
fun SettingsWearTemplateTile(
    content: String,
    onContentChanged: (String) -> Unit
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
            Text(stringResource(R.string.template_tile_help))
            TextField(
                value = content,
                onValueChange = onContentChanged,
                label = {
                    Text(stringResource(id = R.string.template_tile_content))
                },
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
