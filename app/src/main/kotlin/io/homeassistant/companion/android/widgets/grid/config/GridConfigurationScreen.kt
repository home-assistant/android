package io.homeassistant.companion.android.widgets.grid.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.util.FailFast
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun GridWidgetConfigurationScreen(viewModel: GridConfigurationViewModel, onSubmit: (GridConfiguration) -> Unit) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val entities by viewModel.entities.collectAsStateWithLifecycle()
    val config by viewModel.gridConfig.collectAsStateWithLifecycle()

    GridWidgetConfigurationContent(
        servers = servers,
        onServerSelected = viewModel::setServer,
        entities = entities,
        onSubmit = onSubmit,
        config = config,
        onNameChange = viewModel::setLabel,
        onItemAdd = viewModel::addItem,
        onItemEdit = viewModel::editItem,
        onItemMove = viewModel::moveItem,
        onItemDelete = viewModel::deleteItem,
    )
}

@Composable
private fun GridWidgetConfigurationContent(
    servers: List<Server>,
    onServerSelected: (Int) -> Unit,
    entities: List<Entity>,
    config: GridConfiguration,
    onNameChange: (String) -> Unit,
    onItemAdd: (Entity) -> Unit,
    onItemEdit: (Int, Entity) -> Unit,
    onItemMove: (GridItem, GridItem) -> Unit,
    onItemDelete: (Int) -> Unit,
    onSubmit: (config: GridConfiguration) -> Unit,
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
        GridWidgetConfigurationContent(
            modifier = Modifier
                .windowInsetsPadding(safeBottomWindowInsets())
                .padding(padding),
            config = config,
            onNameChange = onNameChange,
            servers = servers,
            onServerSelected = onServerSelected,
            onItemMove = onItemMove,
            entities = entities,
            onItemEdit = onItemEdit,
            onItemDelete = onItemDelete,
            onItemAdd = onItemAdd,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun GridWidgetConfigurationContent(
    config: GridConfiguration,
    onNameChange: (String) -> Unit,
    servers: List<Server>,
    onServerSelected: (Int) -> Unit,
    onItemMove: (GridItem, GridItem) -> Unit,
    entities: List<Entity>,
    onItemEdit: (Int, Entity) -> Unit,
    onItemDelete: (Int) -> Unit,
    onItemAdd: (Entity) -> Unit,
    onSubmit: (GridConfiguration) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
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
                    current = config.serverId,
                    onSelected = { onServerSelected(it) },
                )
            }
        }

        PreferenceSection(
            modifier = Modifier.weight(1f),
            title = stringResource(commonR.string.widget_grid_add_entities),
        ) {
            EntityList(
                onItemMove = onItemMove,
                config = config,
                entities = entities,
                onItemEdit = onItemEdit,
                onItemDelete = onItemDelete,
                onItemAdd = onItemAdd,
            )
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(HADimens.SPACE4),
            onClick = { onSubmit(config) },
        ) {
            Text(stringResource(commonR.string.update_widget))
        }
    }
}

@Composable
private fun EntityList(
    onItemMove: (GridItem, GridItem) -> Unit,
    config: GridConfiguration,
    entities: List<Entity>,
    onItemEdit: (Int, Entity) -> Unit,
    onItemDelete: (Int) -> Unit,
    onItemAdd: (Entity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onItemMove(from.key as GridItem, to.key as GridItem)
    }
    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        itemsIndexed(config.items) { index, item ->
            ReorderableItem(
                state = reorderState,
                key = item,
            ) {
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
                        FailFast.failOnCatch(
                            { "Selected entity not found: $selected" },
                            Unit,
                        ) {
                            val entity = entities.first { it.entityId == selected }
                            onItemEdit(index, entity)
                        }
                        true
                    },
                    onDelete = { onItemDelete(index) },
                )
            }
        }
        item {
            EntityEditorRow(
                entities = entities,
                icon = {
                    Icon(painterResource(R.drawable.ic_plus), null)
                },
                onSelect = { selected ->
                    FailFast.failOnCatch(
                        { "Selected entity not found: $selected" },
                        Unit,
                    ) {
                        val entity = entities.first { it.entityId == selected }
                        onItemAdd(entity)
                    }
                    false
                },
                onDelete = {},
            )
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
            modifier = Modifier.padding(HADimens.SPACE4),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
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
        modifier = modifier.padding(start = HADimens.SPACE12),
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
                serverId = 0,
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
            onServerSelected = {},
            entities = listOf(
                previewEntity1,
                previewEntity2,
            ),
            onNameChange = {},
            onItemAdd = {},
            onItemEdit = { _, _ -> },
            onItemMove = { _, _ -> },
            onItemDelete = {},
            onSubmit = {},
        )
    }
}
