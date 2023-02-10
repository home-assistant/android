package io.homeassistant.companion.android.util.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import io.homeassistant.companion.android.database.server.Server

@Composable
fun ServerDropdownButton(servers: List<Server>, current: Int?, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(servers.firstOrNull { it.id == current }?.friendlyName.orEmpty())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            servers.forEach { server ->
                DropdownMenuItem(onClick = {
                    onSelected(server.id)
                    expanded = false
                }) {
                    Text(text = server.friendlyName, fontSize = 15.sp)
                }
            }
        }
    }
}
