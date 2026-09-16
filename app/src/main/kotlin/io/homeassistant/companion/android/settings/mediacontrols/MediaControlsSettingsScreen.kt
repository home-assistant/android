package io.homeassistant.companion.android.settings.mediacontrols

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.Close
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.ButtonVariant
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.entitySubtitleSeparator
import io.homeassistant.companion.android.common.data.mediacontrols.MediaControlsEntityConfig
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.safeBottomPaddingValues

/** Displays the media controls settings screen, backed by [MediaControlsSettingsViewModel]. */
@Composable
fun MediaControlsSettingsScreen(viewModel: MediaControlsSettingsViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MediaControlsSettingsContent(
        uiState = uiState,
        onServerSelected = viewModel::selectServerId,
        onEntitySelected = viewModel::addEntity,
        onRemoveEntity = viewModel::removeEntity,
        modifier = modifier,
    )
}

@Composable
internal fun MediaControlsSettingsContent(
    uiState: MediaControlsSettingsUiState,
    onServerSelected: (Int) -> Unit,
    onEntitySelected: (String) -> Unit,
    onRemoveEntity: (MediaControlsSelectedEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasMultiServer = uiState.serversDropdownItems.size > 1

    LazyColumn(
        contentPadding = safeBottomPaddingValues(applyHorizontal = false),
        modifier = modifier.padding(top = HADimens.SPACE4),
    ) {
        item {
            DescriptionSection()

            if (hasMultiServer) {
                ServerSelector(
                    items = uiState.serversDropdownItems,
                    selectedServerId = uiState.selectedServerId,
                    onServerSelected = onServerSelected,
                    modifier = Modifier.padding(top = HADimens.SPACE4),
                )
            }

            if (!uiState.isLoading) {
                EntityPickerSection(
                    availableEntities = uiState.availableEntities,
                    onEntitySelected = onEntitySelected,
                    modifier = Modifier.padding(vertical = HADimens.SPACE4),
                )
            }
        }

        configuredEntities(
            isLoading = uiState.isLoading,
            selectedEntities = uiState.selectedEntities,
            hasMultiServer = hasMultiServer,
            onRemoveEntity = onRemoveEntity,
        )
    }
}

@Composable
private fun DescriptionSection() {
    Text(
        text = stringResource(commonR.string.media_controls_description),
        style = HATextStyle.Body,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(horizontal = HADimens.SPACE4),
    )
}

@Composable
private fun ServerSelector(
    items: List<HADropdownItem<Int>>,
    selectedServerId: Int,
    onServerSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = HADimens.SPACE4)) {
        HADropdownMenu(
            items = items,
            selectedKey = selectedServerId,
            onItemSelected = onServerSelected,
            label = stringResource(commonR.string.server_select),
            placeholder = stringResource(commonR.string.server_select),
            modifier = Modifier
                .widthIn(max = MaxButtonWidth)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun EntityPickerSection(
    availableEntities: EntityDisplayState<EntityDisplayWithContext>,
    onEntitySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EntityPicker(
        displayState = availableEntities,
        selectedEntityId = null,
        onSelectionChanged = { entityId ->
            if (entityId != null) {
                onEntitySelected(entityId)
            }
        },
        addButtonText = stringResource(commonR.string.media_controls_select_entity),
        modifier = modifier.padding(horizontal = HADimens.SPACE4),
    )
}

private fun LazyListScope.configuredEntities(
    isLoading: Boolean,
    selectedEntities: List<MediaControlsSelectedEntity>,
    hasMultiServer: Boolean,
    onRemoveEntity: (MediaControlsSelectedEntity) -> Unit,
) {
    if (isLoading) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HADimens.SPACE4),
                contentAlignment = Alignment.Center,
            ) {
                HALoading()
            }
        }
    } else {
        items(
            items = selectedEntities,
            key = { item -> item.config.id },
        ) { item ->
            ConfiguredEntityRow(
                item = item,
                hasMultiServer = hasMultiServer,
                onRemove = { onRemoveEntity(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ConfiguredEntityRow(
    item: MediaControlsSelectedEntity,
    hasMultiServer: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current
    val entityIcon = item.entityForDisplay?.icon?.rememberImageVector()

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
                imageVector = entityIcon,
                colorFilter = ColorFilter.tint(colorScheme.colorTextSecondary),
                contentDescription = null,
                modifier = Modifier.size(HADimens.SPACE6),
            )
        } else {
            Spacer(modifier = Modifier.size(HADimens.SPACE6))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.entityForDisplay?.name ?: item.config.entityId,
                style = HATextStyle.Body,
                color = colorScheme.colorTextPrimary,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val subtitle = listOfNotNull(
                item.serverName.takeIf { hasMultiServer },
                item.entityForDisplay?.subtitle(),
            ).takeIf { it.isNotEmpty() }?.joinToString(entitySubtitleSeparator())

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = HATextStyle.BodyMedium,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HAIconButton(
            icon = Mdi.Close.rememberImageVector(),
            onClick = onRemove,
            contentDescription = stringResource(commonR.string.media_controls_remove_entity),
            variant = ButtonVariant.NEUTRAL,
        )
    }
}

@Preview
@Composable
private fun MediaControlsSettingsContentLoadingPreview() {
    HAThemeForPreview {
        MediaControlsSettingsContent(
            uiState = MediaControlsSettingsUiState(isLoading = true),
            onServerSelected = {},
            onEntitySelected = {},
            onRemoveEntity = {},
        )
    }
}

@Preview
@Composable
private fun MediaControlsSettingsContentEmptyPreview() {
    HAThemeForPreview {
        MediaControlsSettingsContent(
            uiState = MediaControlsSettingsUiState(isLoading = false),
            onServerSelected = {},
            onEntitySelected = {},
            onRemoveEntity = {},
        )
    }
}

@Preview
@Composable
private fun MediaControlsSettingsContentWithEntitiesPreview() {
    HAThemeForPreview {
        MediaControlsSettingsContent(
            uiState = MediaControlsSettingsUiState(
                isLoading = false,
                mediaControlsEntityConfigs = listOf(
                    MediaControlsEntityConfig(serverId = 1, entityId = "media_player.living_room"),
                    MediaControlsEntityConfig(serverId = 1, entityId = "media_player.bedroom"),
                ),
            ),
            onServerSelected = {},
            onEntitySelected = {},
            onRemoveEntity = {},
        )
    }
}
