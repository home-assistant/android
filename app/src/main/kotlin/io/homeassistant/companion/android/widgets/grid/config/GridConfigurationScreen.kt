package io.homeassistant.companion.android.widgets.grid.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.compose.IconicsPainter
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.SingleEntityPicker
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import io.homeassistant.companion.android.util.safeTopWindowInsets

@Composable
fun GridWidgetConfigurationScreen(viewModel: GridConfigurationViewModel, onAddWidget: (GridConfiguration) -> Unit) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val entities by viewModel.entities.collectAsStateWithLifecycle()
    val config by viewModel.gridConfig.collectAsStateWithLifecycle()

    GridWidgetConfigurationContent(
        servers = servers,
        selectedServerId = config.serverId,
        onServerSelected = viewModel::setServer,
        entities = entities,
        onConfigure = onAddWidget,
        config = config,
        onNameChange = viewModel::setLabel,
        onItemAdd = viewModel::addItem,
        onItemEdit = viewModel::editItem,
        onItemDelete = viewModel::deleteItem,
    )
}

@Composable
private fun GridWidgetConfigurationContent(
    servers: List<Server>,
    selectedServerId: Int,
    onServerSelected: (Int) -> Unit,
    entities: List<Entity>,
    config: GridConfiguration,
    onNameChange: (String) -> Unit,
    onItemAdd: (Entity) -> Unit,
    onItemEdit: (Int, Entity) -> Unit,
    onItemDelete: (Int) -> Unit,
    onConfigure: (config: GridConfiguration) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(commonR.string.widget_grid_title)) },
                windowInsets = safeTopWindowInsets(),
                backgroundColor = colorResource(commonR.color.colorBackground),
                contentColor = colorResource(commonR.color.colorOnBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .windowInsetsPadding(safeBottomWindowInsets())
                .padding(padding),
        ) {
            PreferenceSection {
                var name by remember(config.label) { mutableStateOf(config.label.orEmpty()) }
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    label = { Text(stringResource(commonR.string.widget_grid_name)) },
                    singleLine = true,
                    onValueChange = {
                        onNameChange(it)
                        name = it
                    },
                )

                if (servers.size > 1) {
                    ServerExposedDropdownMenu(
                        servers = servers,
                        current = selectedServerId,
                        onSelected = { onServerSelected(it) },
                    )
                }
            }

            PreferenceSection(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f),
                title = stringResource(commonR.string.widget_grid_add_entities),
            ) {
                config.items.forEachIndexed { i, item ->
                    val iconPainter = remember(item.icon) {
                        CommunityMaterial.getIconByMdiName(item.icon)?.let {
                            IconicsPainter(it)
                        }
                    }
                    EntityEditorRow(
                        entityId = item.entityId,
                        entities = entities,
                        icon = iconPainter?.let { { Icon(it, null) } },
                        onSelect = { selected ->
                            val entity = entities.first { it.entityId == selected }
                            onItemEdit(i, entity)
                            true
                        },
                        onDelete = { onItemDelete(i) },
                    )
                }

                EntityEditorRow(
                    entities = entities,
                    icon = {
                        Icon(painterResource(R.drawable.ic_plus), null)
                    },
                    onSelect = { selected ->
                        val entity = entities.first { it.entityId == selected }
                        onItemAdd(entity)
                        false
                    },
                    onDelete = {},
                )
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClick = { onConfigure(config) },
            ) {
                Text(stringResource(commonR.string.update_widget))
            }
        }
    }
}

@Composable
private fun EntityEditorRow(
    entities: List<Entity>,
    onSelect: (String) -> Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    entityId: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        SingleEntityPicker(
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(commonR.string.entity)) },
            entities = entities,
            currentEntity = entityId,
            onEntityCleared = onDelete,
            onEntitySelected = onSelect,
        )
    }
}

@Composable
private fun PreferenceSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            title?.let { SectionHeader(title) }
            content()
        }

        Divider(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(start = 48.dp),
        text = title,
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.primary,
    )
}

@Preview(showBackground = true)
@Composable
private fun GridConfigurationPreview() {
    HomeAssistantAppTheme {
        GridWidgetConfigurationContent(
            config = GridConfiguration(
                label = "Home lights",
                items = listOf(
                    GridItem("Bedroom", "mdi:lightbulb"),
                    GridItem("Bedroom", "mdi:lightbulb"),
                    GridItem("Living room", "mdi:lightbulb"),
                ),
            ),
            servers = listOf(
                previewServer1,
                previewServer2,
            ),
            selectedServerId = 0,
            onServerSelected = {},
            entities = listOf(
                previewEntity1,
                previewEntity2,
            ),
            onNameChange = {},
            onItemAdd = {},
            onItemEdit = { _, _ -> },
            onItemDelete = {},
            onConfigure = {},
        )
    }
}
