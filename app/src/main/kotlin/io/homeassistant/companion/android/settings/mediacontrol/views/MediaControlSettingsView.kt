package io.homeassistant.companion.android.settings.mediacontrol.views

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.settings.mediacontrol.MediaControlSettingsUiState
import io.homeassistant.companion.android.settings.mediacontrol.MediaControlSettingsViewModel
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Outer composable that extracts state from the ViewModel and delegates to the stateless content. */
@Composable
fun MediaControlSettingsView(viewModel: MediaControlSettingsViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HATheme {
        MediaControlSettingsContent(
            uiState = uiState,
            onServerSelected = viewModel::selectServerId,
            onEntitySelected = viewModel::addEntity,
            onRemoveEntity = viewModel::removeEntity,
            onMove = viewModel::onMove,
            onSave = viewModel::saveConfiguration,
            onClearAll = viewModel::clearAllConfiguration,
            modifier = modifier,
        )
    }
}

@Composable
internal fun MediaControlSettingsContent(
    uiState: MediaControlSettingsUiState,
    onServerSelected: (Int) -> Unit,
    onEntitySelected: (String) -> Unit,
    onRemoveEntity: (Int) -> Unit,
    onMove: (LazyListItemInfo, LazyListItemInfo) -> Unit,
    onSave: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from, to)
    }

    val availableEntities = uiState.entitiesPerServer[uiState.selectedServerId]
        ?.filter { entity ->
            uiState.configuredEntities.none {
                it.serverId == uiState.selectedServerId && it.entityId == entity.entityId
            }
        } ?: emptyList()

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(all = 16.dp) + safeBottomPaddingValues(applyHorizontal = false),
        modifier = modifier,
    ) {
        item {
            Text(
                text = stringResource(R.string.media_control_description),
                style = HATextStyle.Body,
                color = colorScheme.colorTextSecondary,
            )
            Spacer(modifier = Modifier.size(16.dp))
        }

        if (uiState.servers.size > 1) {
            item {
                ServerExposedDropdownMenu(
                    servers = uiState.servers,
                    current = uiState.selectedServerId,
                    onSelected = onServerSelected,
                    title = R.string.server,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
        }

        item {
            EntityPicker(
                entities = availableEntities,
                selectedEntityId = null,
                onEntitySelectedId = onEntitySelected,
                onEntityCleared = {},
                addButtonText = stringResource(R.string.media_control_select_entity),
                entityRegistry = uiState.entityRegistryPerServer[uiState.selectedServerId] ?: emptyList(),
                deviceRegistry = uiState.deviceRegistryPerServer[uiState.selectedServerId] ?: emptyList(),
                areaRegistry = uiState.areaRegistryPerServer[uiState.selectedServerId] ?: emptyList(),
            )
            if (uiState.configuredEntities.isNotEmpty()) {
                Spacer(modifier = Modifier.size(8.dp))
                HorizontalDivider()
            }
        }

        items(
            items = uiState.configuredEntities,
            key = { it },
        ) { config ->
            ReorderableItem(state = reorderState, key = config) { isDragging ->
                ConfiguredEntityRow(
                    config = config,
                    servers = uiState.servers,
                    entitiesPerServer = uiState.entitiesPerServer,
                    onRemove = { onRemoveEntity(uiState.configuredEntities.indexOf(config)) },
                    isDragging = isDragging,
                )
                if (config != uiState.configuredEntities.last()) {
                    HorizontalDivider()
                }
            }
        }

        item {
            if (uiState.configuredEntities.isNotEmpty()) {
                Spacer(modifier = Modifier.size(8.dp))
            }
            HAFilledButton(
                text = stringResource(R.string.save),
                onClick = onSave,
                enabled = uiState.configuredEntities.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.configuredEntities.isNotEmpty()) {
                Spacer(modifier = Modifier.size(8.dp))
                HAPlainButton(
                    text = stringResource(R.string.media_control_clear),
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.ConfiguredEntityRow(
    config: MediaControlEntityConfig,
    servers: List<Server>,
    entitiesPerServer: Map<Int, List<Entity>>,
    onRemove: () -> Unit,
    isDragging: Boolean,
) {
    val colorScheme = LocalHAColorScheme.current
    val elevation = animateDpAsState(targetValue = if (isDragging) 8.dp else 0.dp)
    val serverName = servers.firstOrNull { it.id == config.serverId }?.friendlyName
    val entityName = entitiesPerServer[config.serverId]
        ?.firstOrNull { it.entityId == config.entityId }
        ?.attributes?.get("friendly_name") as? String
    val displayName = entityName ?: config.entityId
    val subtitle = if (servers.size > 1 && serverName != null) serverName else null

    Surface(shadowElevation = elevation.value) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .longPressDraggableHandle()
                .padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = HATextStyle.Body,
                    color = colorScheme.colorTextPrimary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = HATextStyle.BodyMedium,
                        color = colorScheme.colorTextSecondary,
                    )
                }
            }
            HAIconButton(
                icon = Icons.Default.Clear,
                onClick = onRemove,
                contentDescription = stringResource(R.string.media_control_remove_entity),
                variant = ButtonVariant.NEUTRAL,
            )
            Image(
                asset = CommunityMaterial.Icon.cmd_drag_horizontal_variant,
                contentDescription = stringResource(R.string.hold_to_reorder),
                colorFilter = ColorFilter.tint(colorScheme.colorTextSecondary),
                modifier = Modifier
                    .size(width = 40.dp, height = 24.dp)
                    .padding(end = 16.dp),
            )
        }
    }
}

@Preview
@Composable
private fun MediaControlSettingsContentEmptyPreview() {
    HAThemeForPreview {
        MediaControlSettingsContent(
            uiState = MediaControlSettingsUiState(),
            onServerSelected = {},
            onEntitySelected = {},
            onRemoveEntity = {},
            onMove = { _, _ -> },
            onSave = {},
            onClearAll = {},
        )
    }
}

@Preview
@Composable
private fun MediaControlSettingsContentWithEntitiesPreview() {
    HAThemeForPreview {
        MediaControlSettingsContent(
            uiState = MediaControlSettingsUiState(
                configuredEntities = listOf(
                    MediaControlEntityConfig(serverId = 1, entityId = "media_player.living_room"),
                    MediaControlEntityConfig(serverId = 1, entityId = "media_player.bedroom"),
                ),
            ),
            onServerSelected = {},
            onEntitySelected = {},
            onRemoveEntity = {},
            onMove = { _, _ -> },
            onSave = {},
            onClearAll = {},
        )
    }
}
