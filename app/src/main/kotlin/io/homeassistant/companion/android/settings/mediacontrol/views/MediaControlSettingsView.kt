package io.homeassistant.companion.android.settings.mediacontrol.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.settings.mediacontrol.MediaControlSettingsUiState
import io.homeassistant.companion.android.settings.mediacontrol.MediaControlSettingsViewModel
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.safeBottomPaddingValues

/** Outer composable that extracts state from the ViewModel and delegates to the stateless content. */
@Composable
fun MediaControlSettingsView(viewModel: MediaControlSettingsViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HATheme {
        MediaControlSettingsContent(
            uiState = uiState,
            onAddEntity = viewModel::showAddEntity,
            onCancelAddEntity = viewModel::cancelAddEntity,
            onPendingServerSelected = viewModel::selectPendingServerId,
            onPendingEntitySelected = viewModel::addPendingEntity,
            onRemoveEntity = viewModel::removeEntity,
            onSave = viewModel::saveConfiguration,
            onClearAll = viewModel::clearAllConfiguration,
            modifier = modifier,
        )
    }
}

@Composable
internal fun MediaControlSettingsContent(
    uiState: MediaControlSettingsUiState,
    onAddEntity: () -> Unit,
    onCancelAddEntity: () -> Unit,
    onPendingServerSelected: (Int) -> Unit,
    onPendingEntitySelected: (String) -> Unit,
    onRemoveEntity: (Int) -> Unit,
    onSave: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val colorScheme = LocalHAColorScheme.current
    val pendingEntities = uiState.entitiesPerServer[uiState.pendingServerId] ?: emptyList()

    Box(modifier = modifier.verticalScroll(scrollState)) {
        Column(
            modifier = Modifier
                .padding(safeBottomPaddingValues(applyHorizontal = false))
                .padding(all = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.media_control_description),
                style = HATextStyle.Body,
                color = colorScheme.colorTextSecondary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            uiState.configuredEntities.forEachIndexed { index, config ->
                ConfiguredEntityRow(
                    config = config,
                    servers = uiState.servers,
                    entitiesPerServer = uiState.entitiesPerServer,
                    onRemove = { onRemoveEntity(index) },
                )
                if (index < uiState.configuredEntities.lastIndex) {
                    HorizontalDivider()
                }
            }

            if (uiState.configuredEntities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.showAddSlot) {
                AddEntitySlot(
                    servers = uiState.servers,
                    pendingServerId = uiState.pendingServerId,
                    pendingEntities = pendingEntities,
                    entityRegistry = uiState.entityRegistryPerServer[uiState.pendingServerId] ?: emptyList(),
                    deviceRegistry = uiState.deviceRegistryPerServer[uiState.pendingServerId] ?: emptyList(),
                    areaRegistry = uiState.areaRegistryPerServer[uiState.pendingServerId] ?: emptyList(),
                    onServerSelected = onPendingServerSelected,
                    onEntitySelected = onPendingEntitySelected,
                    onCancel = onCancelAddEntity,
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                HAFilledButton(
                    text = stringResource(R.string.media_control_add_entity),
                    onClick = onAddEntity,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            HAFilledButton(
                text = stringResource(R.string.save),
                onClick = onSave,
                enabled = uiState.configuredEntities.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.configuredEntities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
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
private fun ConfiguredEntityRow(
    config: MediaControlEntityConfig,
    servers: List<Server>,
    entitiesPerServer: Map<Int, List<Entity>>,
    onRemove: () -> Unit,
) {
    val colorScheme = LocalHAColorScheme.current
    val serverName = servers.firstOrNull { it.id == config.serverId }?.friendlyName
    val entityName = entitiesPerServer[config.serverId]
        ?.firstOrNull { it.entityId == config.entityId }
        ?.attributes?.get("friendly_name") as? String
    val displayName = entityName ?: config.entityId
    val subtitle = if (servers.size > 1 && serverName != null) serverName else null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
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
    }
}

@Composable
private fun AddEntitySlot(
    servers: List<Server>,
    pendingServerId: Int,
    pendingEntities: List<Entity>,
    entityRegistry: List<EntityRegistryResponse>,
    deviceRegistry: List<DeviceRegistryResponse>,
    areaRegistry: List<AreaRegistryResponse>,
    onServerSelected: (Int) -> Unit,
    onEntitySelected: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        if (servers.size > 1 || servers.none { it.id == pendingServerId }) {
            ServerExposedDropdownMenu(
                servers = servers,
                current = pendingServerId,
                onSelected = onServerSelected,
                title = R.string.server,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        EntityPicker(
            entities = pendingEntities,
            selectedEntityId = null,
            onEntitySelectedId = onEntitySelected,
            onEntityCleared = {},
            addButtonText = stringResource(R.string.media_control_select_entity),
            entityRegistry = entityRegistry,
            deviceRegistry = deviceRegistry,
            areaRegistry = areaRegistry,
        )

        Spacer(modifier = Modifier.height(8.dp))
        HAPlainButton(
            text = stringResource(R.string.cancel),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun MediaControlSettingsContentEmptyPreview() {
    HAThemeForPreview {
        MediaControlSettingsContent(
            uiState = MediaControlSettingsUiState(),
            onAddEntity = {},
            onCancelAddEntity = {},
            onPendingServerSelected = {},
            onPendingEntitySelected = {},
            onRemoveEntity = {},
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
            onAddEntity = {},
            onCancelAddEntity = {},
            onPendingServerSelected = {},
            onPendingEntitySelected = {},
            onRemoveEntity = {},
            onSave = {},
            onClearAll = {},
        )
    }
}
