package io.homeassistant.companion.android.util.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.common.WidgetUtils

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ExposedDropdownMenu(
    label: String,
    keys: List<String>,
    currentIndex: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        TextField(
            readOnly = true,
            value = currentIndex?.let { keys[it] } ?: "",
            onValueChange = { },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            keys.forEachIndexed { index, key ->
                DropdownMenuItem(onClick = {
                    onSelected(index)
                    expanded = false
                    focusManager.clearFocus()
                }) {
                    Text(key)
                }
            }
        }
    }
}

@Composable
fun ServerExposedDropdownMenu(
    servers: List<Server>,
    current: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes title: Int = commonR.string.server_select,
) {
    val keys = servers.map { it.friendlyName }
    val ids = servers.map { it.id }
    val currentIndex = servers.indexOfFirst { it.id == current }.takeUnless { it == -1 }
    ExposedDropdownMenu(
        label = stringResource(title),
        keys = keys,
        currentIndex = currentIndex,
        onSelected = { onSelected(ids[it]) },
        modifier = modifier,
    )
}

@Composable
fun WidgetBackgroundTypeExposedDropdownMenu(
    current: WidgetBackgroundType?,
    onSelected: (WidgetBackgroundType) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes title: Int = commonR.string.widget_background_type_title,
) {
    val context = LocalContext.current
    val keys = remember { WidgetUtils.getBackgroundOptionList(context) }
    val currentIndex =
        remember(current) { current?.let { WidgetUtils.getSelectedBackgroundOption(context, current, keys) } }
    ExposedDropdownMenu(
        label = stringResource(title),
        keys = keys.toList(),
        currentIndex = currentIndex,
        onSelected = { onSelected(WidgetUtils.getWidgetBackgroundType(context, keys[it])) },
        modifier = modifier,
    )
}
