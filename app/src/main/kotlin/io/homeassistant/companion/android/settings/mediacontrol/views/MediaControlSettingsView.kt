package io.homeassistant.companion.android.settings.mediacontrol.views

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
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
            onReorderComplete = viewModel::onReorderComplete,
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
    onReorderComplete: () -> Unit,
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
        contentPadding = PaddingValues(vertical = 16.dp) + safeBottomPaddingValues(applyHorizontal = false),
        modifier = modifier,
    ) {
        item {
            Text(
                text = stringResource(R.string.media_control_description),
                style = HATextStyle.Body,
                color = colorScheme.colorTextSecondary,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
        }

        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            if (uiState.servers.size > 1) {
                item {
                    ServerExposedDropdownMenu(
                        servers = uiState.servers,
                        current = uiState.selectedServerId,
                        onSelected = onServerSelected,
                        title = R.string.server,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        itemsIndexed(
            items = uiState.configuredEntities,
            key = { _, config -> "${config.serverId}_${config.entityId}" },
        ) { index, config ->
            ReorderableItem(state = reorderState, key = "${config.serverId}_${config.entityId}") { isDragging ->
                ConfiguredEntityRow(
                    config = config,
                    subtitle = if (uiState.servers.size > 1) {
                        uiState.servers.firstOrNull { it.id == config.serverId }?.friendlyName
                    } else {
                        null
                    },
                    entityName = uiState.entitiesPerServer[config.serverId]
                        ?.firstOrNull { it.entityId == config.entityId }
                        ?.friendlyName,
                    onRemove = { onRemoveEntity(index) },
                    onReorderComplete = onReorderComplete,
                    isDragging = isDragging,
                )
                if (index < uiState.configuredEntities.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.ConfiguredEntityRow(
    config: MediaControlEntityConfig,
    subtitle: String?,
    entityName: String?,
    onRemove: () -> Unit,
    onReorderComplete: () -> Unit,
    isDragging: Boolean,
) {
    val colorScheme = LocalHAColorScheme.current
    val elevation = animateDpAsState(targetValue = if (isDragging) 8.dp else 0.dp)
    val displayName = entityName ?: config.entityId

    Surface(color = colorScheme.colorSurfaceLow, shadowElevation = elevation.value) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .longPressDraggableHandle(onDragStopped = { onReorderComplete() })
                .padding(vertical = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = displayName,
                    style = HATextStyle.Body,
                    color = colorScheme.colorTextPrimary,
                    textAlign = TextAlign.Start,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = HATextStyle.BodyMedium,
                        color = colorScheme.colorTextSecondary,
                        textAlign = TextAlign.Start,
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
private fun MediaControlSettingsContentLoadingPreview() {
    HAThemeForPreview {
        MediaControlSettingsContent(
            uiState = MediaControlSettingsUiState(isLoading = true),
            onServerSelected = {},
            onEntitySelected = {},
            onRemoveEntity = {},
            onMove = { _, _ -> },
            onReorderComplete = {},
        )
    }
}

@Preview
@Composable
private fun MediaControlSettingsContentEmptyPreview() {
    HAThemeForPreview {
        MediaControlSettingsContent(
            uiState = MediaControlSettingsUiState(isLoading = false),
            onServerSelected = {},
            onEntitySelected = {},
            onRemoveEntity = {},
            onMove = { _, _ -> },
            onReorderComplete = {},
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
            onReorderComplete = {},
        )
    }
}
