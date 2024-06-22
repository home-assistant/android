package io.homeassistant.companion.android.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.compose.ModalBottomSheet

@Composable
fun ServerChooserView(
    servers: List<Server>,
    onServerSelected: (Int) -> Unit
) {
    ModalBottomSheet(title = stringResource(commonR.string.server_select)) {
        servers.forEach {
            ServerChooserRow(server = it, onServerSelected = onServerSelected)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ServerChooserRow(
    server: Server,
    onServerSelected: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onServerSelected(server.id) }
    ) {
        Text(
            text = server.friendlyName,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    Divider(modifier = Modifier.padding(horizontal = 16.dp))
}
