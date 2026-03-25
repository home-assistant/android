package io.homeassistant.companion.android.settings.mediacontrol.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
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
            servers = uiState.servers,
            entities = uiState.entities,
            entityRegistry = uiState.entityRegistry,
            deviceRegistry = uiState.deviceRegistry,
            areaRegistry = uiState.areaRegistry,
            selectedServerId = uiState.selectedServerId,
            selectedEntityId = uiState.selectedEntityId,
            isConfigured = uiState.isConfigured,
            onServerSelected = viewModel::selectServerId,
            onEntitySelected = viewModel::selectEntityId,
            onEntityCleared = { viewModel.selectEntityId("") },
            onSave = viewModel::saveConfiguration,
            onClear = viewModel::clearConfiguration,
            modifier = modifier,
        )
    }
}

@Composable
internal fun MediaControlSettingsContent(
    servers: List<Server>,
    entities: List<Entity>,
    entityRegistry: List<EntityRegistryResponse>,
    deviceRegistry: List<DeviceRegistryResponse>,
    areaRegistry: List<AreaRegistryResponse>,
    selectedServerId: Int,
    selectedEntityId: String,
    isConfigured: Boolean,
    onServerSelected: (Int) -> Unit,
    onEntitySelected: (String) -> Unit,
    onEntityCleared: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val colorScheme = LocalHAColorScheme.current
    val saveEnabled = selectedEntityId.isNotBlank() &&
        servers.any { it.id == selectedServerId } &&
        entities.any { it.entityId == selectedEntityId }

    Box(
        modifier = modifier.verticalScroll(scrollState),
    ) {
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

            if (servers.size > 1 || servers.none { it.id == selectedServerId }) {
                ServerExposedDropdownMenu(
                    servers = servers,
                    current = selectedServerId,
                    onSelected = onServerSelected,
                    title = R.string.server,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            EntityPicker(
                entities = entities,
                selectedEntityId = selectedEntityId,
                onEntitySelectedId = onEntitySelected,
                onEntityCleared = onEntityCleared,
                addButtonText = stringResource(R.string.media_control_select_entity),
                entityRegistry = entityRegistry,
                deviceRegistry = deviceRegistry,
                areaRegistry = areaRegistry,
            )

            Spacer(modifier = Modifier.height(16.dp))

            HAFilledButton(
                text = stringResource(R.string.save),
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                HAPlainButton(
                    text = stringResource(R.string.media_control_clear),
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun MediaControlSettingsContentPreview() {
    HAThemeForPreview {
        MediaControlSettingsContent(
            servers = emptyList(),
            entities = emptyList(),
            entityRegistry = emptyList(),
            deviceRegistry = emptyList(),
            areaRegistry = emptyList(),
            selectedServerId = 0,
            selectedEntityId = "",
            isConfigured = false,
            onServerSelected = {},
            onEntitySelected = {},
            onEntityCleared = {},
            onSave = {},
            onClear = {},
        )
    }
}

@Preview
@Composable
private fun MediaControlSettingsContentConfiguredPreview() {
    HAThemeForPreview {
        MediaControlSettingsContent(
            servers = emptyList(),
            entities = emptyList(),
            entityRegistry = emptyList(),
            deviceRegistry = emptyList(),
            areaRegistry = emptyList(),
            selectedServerId = 1,
            selectedEntityId = "media_player.living_room",
            isConfigured = true,
            onServerSelected = {},
            onEntitySelected = {},
            onEntityCleared = {},
            onSave = {},
            onClear = {},
        )
    }
}
