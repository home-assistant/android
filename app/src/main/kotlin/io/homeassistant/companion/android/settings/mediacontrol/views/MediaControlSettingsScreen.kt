package io.homeassistant.companion.android.settings.mediacontrol.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.compose.Image
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.settings.mediacontrol.ConfiguredEntityItem
import io.homeassistant.companion.android.settings.mediacontrol.MediaControlSettingsUiState
import io.homeassistant.companion.android.settings.mediacontrol.MediaControlSettingsViewModel
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues

/** Displays the media controls settings screen, backed by [MediaControlSettingsViewModel]. */
@Composable
fun MediaControlSettingsScreen(viewModel: MediaControlSettingsViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MediaControlSettingsContent(
        uiState = uiState,
        onServerSelected = viewModel::selectServerId,
        onEntitySelected = viewModel::addEntity,
        onRemoveEntity = viewModel::removeEntity,
        modifier = modifier,
    )
}

@Composable
internal fun MediaControlSettingsContent(
    uiState: MediaControlSettingsUiState,
    onServerSelected: (Int) -> Unit,
    onEntitySelected: (String) -> Unit,
    onRemoveEntity: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = HADimens.SPACE4) + safeBottomPaddingValues(applyHorizontal = false),
        modifier = modifier,
    ) {
        item { DescriptionSection() }

        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = HADimens.SPACE4),
                    contentAlignment = Alignment.Center,
                ) {
                    HALoading()
                }
            }
        } else {
            if (uiState.servers.size > 1) {
                item(key = "server_dropdown") {
                    ServerDropdownSection(
                        uiState = uiState,
                        onServerSelected = onServerSelected,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "entity_picker") {
                EntityPickerSection(
                    uiState = uiState,
                    onEntitySelected = onEntitySelected,
                    modifier = Modifier.animateItem(),
                )
            }

            itemsIndexed(
                items = uiState.configuredEntityItems,
                key = { _, item -> item.config.id },
            ) { index, item ->
                ConfiguredEntityRow(
                    item = item,
                    onRemove = { onRemoveEntity(index) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun DescriptionSection() {
    val colorScheme = LocalHAColorScheme.current
    Text(
        text = stringResource(R.string.media_control_description),
        style = HATextStyle.Body,
        color = colorScheme.colorTextPrimary,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(horizontal = HADimens.SPACE4),
    )
    Spacer(modifier = Modifier.size(HADimens.SPACE4))
}

@Composable
private fun ServerDropdownSection(
    uiState: MediaControlSettingsUiState,
    onServerSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HADropdownMenu(
            items = uiState.servers.map { HADropdownItem(key = it.id, label = it.friendlyName) },
            selectedKey = uiState.selectedServerId,
            onItemSelected = onServerSelected,
            label = stringResource(R.string.server),
            modifier = Modifier.fillMaxWidth().padding(horizontal = HADimens.SPACE4),
        )
        Spacer(modifier = Modifier.size(HADimens.SPACE2))
    }
}

@Composable
private fun EntityPickerSection(
    uiState: MediaControlSettingsUiState,
    onEntitySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EntityPicker(
        entities = uiState.availableEntities,
        selectedEntityId = null,
        onEntitySelectedId = onEntitySelected,
        onEntityCleared = {},
        addButtonText = stringResource(R.string.media_control_select_entity),
        entityRegistry = uiState.entityRegistryForServer(uiState.selectedServerId),
        deviceRegistry = uiState.deviceRegistryForServer(uiState.selectedServerId),
        areaRegistry = uiState.areaRegistryForServer(uiState.selectedServerId),
        modifier = modifier.padding(horizontal = HADimens.SPACE4),
    )
}

@Composable
private fun ConfiguredEntityRow(
    item: ConfiguredEntityItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current
    val context = LocalContext.current
    val entityIcon = remember(item.entity) { item.entity?.getIcon(context) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        modifier = modifier
            .fillMaxWidth()
            .background(colorScheme.colorSurfaceLow)
            .heightIn(min = HADimens.SPACE18)
            .padding(vertical = HADimens.SPACE1, horizontal = HADimens.SPACE4),
    ) {
        if (entityIcon != null) {
            Image(
                asset = entityIcon,
                colorFilter = ColorFilter.tint(colorScheme.colorTextSecondary),
                contentDescription = null,
                modifier = Modifier.size(HADimens.SPACE6),
            )
        } else {
            Spacer(modifier = Modifier.size(HADimens.SPACE6))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = HATextStyle.Body,
                color = colorScheme.colorTextPrimary,
                textAlign = TextAlign.Start,
            )
            Text(
                text = item.config.entityId,
                style = HATextStyle.BodyMedium,
                color = colorScheme.colorTextSecondary,
                textAlign = TextAlign.Start,
            )
        }
        HAIconButton(
            icon = Icons.Default.Clear,
            onClick = onRemove,
            contentDescription = stringResource(R.string.media_control_remove_entity),
            variant = ButtonVariant.NEUTRAL,
        )
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
        )
    }
}

@Preview
@Composable
private fun MediaControlSettingsContentWithEntitiesPreview() {
    HAThemeForPreview {
        MediaControlSettingsContent(
            uiState = MediaControlSettingsUiState(
                isLoading = false,
                configuredEntityItems = listOf(
                    ConfiguredEntityItem(
                        config = MediaControlEntityConfig(serverId = 1, entityId = "media_player.living_room"),
                        name = "Living Room",
                        entity = null,
                    ),
                    ConfiguredEntityItem(
                        config = MediaControlEntityConfig(serverId = 1, entityId = "media_player.bedroom"),
                        name = "Bedroom",
                        entity = null,
                    ),
                ),
            ),
            onServerSelected = {},
            onEntitySelected = {},
            onRemoveEntity = {},
        )
    }
}
