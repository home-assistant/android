package io.homeassistant.companion.android.widgets.grid.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mikepenz.iconics.compose.IconicsPainter
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName

@Composable
fun GridConfiguration(
    config: GridConfiguration,
    onNameChange: (String) -> Unit,
    onRequireAuthenticationChange: (Boolean) -> Unit,
    onItemAdd: (GridItem) -> Unit,
    onItemEdit: (Int, GridItem) -> Unit,
    onItemDelete: (Int) -> Unit,
    onConfigure: (config: GridConfiguration) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PreferenceSection(stringResource(R.string.widget_grid_preferences)) {
                var showDialog by remember { mutableStateOf(false) }
                if (showDialog) {
                    NameConfigurationDialog(
                        initialName = config.label.orEmpty(),
                        onNameChange = {
                            onNameChange(it)
                            showDialog = false
                        },
                        onDismissRequest = { showDialog = false }
                    )
                }
                PreferenceItem(
                    name = stringResource(R.string.widget_grid_name),
                    subtitle = config.label,
                    onClick = { showDialog = true },
                    icon = { Icon(Icons.Default.Edit, null) }
                )
                PreferenceItem(
                    name = stringResource(R.string.widget_checkbox_require_authentication),
                    checked = config.requireAuthentication,
                    onClick = { onRequireAuthenticationChange(!config.requireAuthentication) },
                    icon = { Icon(Icons.Default.Lock, null) }
                )
            }

            PreferenceSection(stringResource(R.string.grid_widget_actions)) {
                var showDialog by remember { mutableStateOf(false) }
                var currentAction by remember { mutableIntStateOf(-1) }
                config.items.forEachIndexed { i, it ->
                    val iconPainter = remember(it.icon) {
                        CommunityMaterial.getIconByMdiName(it.icon)?.let {
                            IconicsPainter(it)
                        }
                    }
                    PreferenceItem(
                        name = it.label,
                        onClick = {
                            currentAction = i
                            showDialog = true
                        },
                        icon = { iconPainter?.let { Icon(it, null) } }
                    )
                }

                if (showDialog) {
                    ActionConfigurationDialog(
                        onDismissRequest = {
                            currentAction = -1
                            showDialog = false
                        },
                        onActionChange = {
                            if (currentAction >= 0) {
                                onItemEdit(currentAction, it)
                            } else {
                                onItemAdd(it)
                            }
                            currentAction = -1
                            showDialog = false
                        },
                        onDelete = {
                            onItemDelete(currentAction)
                            currentAction = -1
                            showDialog = false
                        },
                        initialItem = currentAction.takeIf { it >= 0 }?.let { config.items[it] }
                    )
                }
                PreferenceItem(
                    name = stringResource(R.string.widget_grid_add_action),
                    onClick = { showDialog = true },
                    icon = { Icon(Icons.Default.Add, null) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            modifier = Modifier.align(Alignment.End),
            onClick = { onConfigure(config) }
        ) {
            Text(stringResource(R.string.save))
        }
    }
}

@Composable
private fun PreferenceSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(title)
        content()
        Divider(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        modifier = modifier.padding(start = 48.dp),
        text = title,
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.primary
    )
}

@Composable
private fun PreferenceItem(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.CenterStart,
            content = {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colors.primary
                ) {
                    icon?.invoke()
                }
            }
        )
        Column(verticalArrangement = Arrangement.Center) {
            Text(name, style = MaterialTheme.typography.body1)
            subtitle?.let { Text(subtitle, style = MaterialTheme.typography.body2) }
        }
    }
}

@Composable
private fun PreferenceItem(
    name: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.CenterStart,
            content = {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colors.primary
                ) {
                    icon?.invoke()
                }
            }
        )
        Column(verticalArrangement = Arrangement.Center) {
            Text(name, style = MaterialTheme.typography.body1)
            subtitle?.let { Text(subtitle, style = MaterialTheme.typography.body2) }
        }

        Spacer(Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun NameConfigurationDialog(
    onNameChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = ""
) {
    var name by remember { mutableStateOf(initialName) }
    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    label = { Text(stringResource(R.string.widget_grid_name)) },
                    onValueChange = { name = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel).uppercase()) }
                    Button(onClick = { onNameChange(name) }) { Text(stringResource(R.string.ok).uppercase()) }
                }
            }
        }
    }
}

@Composable
private fun ActionConfigurationDialog(
    onDismissRequest: () -> Unit,
    onActionChange: (GridItem) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    initialItem: GridItem? = null
) {
    var gridItem by remember { mutableStateOf(initialItem ?: GridItem()) }
    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.widget_grid_edit_action), style = MaterialTheme.typography.h6)

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = gridItem.label,
                    onValueChange = { gridItem = gridItem.copy(label = it) },
                    label = { Text(stringResource(R.string.widget_grid_name)) }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = gridItem.icon,
                    onValueChange = { gridItem = gridItem.copy(icon = it) },
                    label = { Text(stringResource(R.string.icon)) }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = gridItem.domain,
                    onValueChange = { gridItem = gridItem.copy(domain = it) },
                    label = { Text(stringResource(R.string.widget_text_hint_action_domain)) }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = gridItem.service,
                    onValueChange = { gridItem = gridItem.copy(service = it) },
                    label = { Text(stringResource(R.string.widget_grid_service)) }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = gridItem.entityId,
                    onValueChange = { gridItem = gridItem.copy(entityId = it) },
                    label = { Text(stringResource(R.string.widget_text_hint_action_data)) }
                )

                Row {
                    IconButton(
                        onClick = onDelete
                    ) {
                        Icon(Icons.Default.Delete, null)
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel).uppercase()) }
                        Button(onClick = { onActionChange(gridItem) }) { Text(stringResource(R.string.ok).uppercase()) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferencePreview() {
    HomeAssistantAppTheme {
        Column {
            PreferenceSection(
                modifier = Modifier.fillMaxWidth(),
                title = "First section"
            ) {
                PreferenceItem("Test", subtitle = "Value", icon = { Icon(Icons.Default.QuestionMark, null) }, onClick = {})
            }
            PreferenceSection(
                modifier = Modifier.fillMaxWidth(),
                title = "Second section"
            ) {
                PreferenceItem("Test", subtitle = "Value", icon = { Icon(Icons.Default.QuestionMark, null) }, onClick = {})
                PreferenceItem("Test", subtitle = "Value", icon = { Icon(Icons.Default.QuestionMark, null) }, onClick = {})
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GridConfigurationPreview() {
    HomeAssistantAppTheme {
        GridConfiguration(
            config = GridConfiguration(
                label = "Home lights",
                items = listOf(
                    GridItem("Bedroom", "mdi:lightbulb"),
                    GridItem("Living room", "mdi:lightbulb")
                )
            ),
            onNameChange = {},
            onRequireAuthenticationChange = {},
            onItemAdd = {},
            onItemEdit = { _, _ -> },
            onItemDelete = {},
            onConfigure = {}
        )
    }
}

@Preview
@Composable
private fun NameDialogPreview() {
    HomeAssistantAppTheme {
        NameConfigurationDialog(initialName = "", onNameChange = {}, onDismissRequest = {})
    }
}

@Preview
@Composable
private fun ActionDialogPreview() {
    HomeAssistantAppTheme {
        ActionConfigurationDialog(
            onDismissRequest = {},
            onActionChange = {},
            onDelete = {},
            initialItem = GridItem("Bedroom")
        )
    }
}
