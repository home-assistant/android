package io.homeassistant.companion.android.settings.mediacontrol.views

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import io.homeassistant.companion.android.common.data.mediacontrol.MediaControlEntityConfig
import io.homeassistant.companion.android.settings.mediacontrol.MediaControlSelectedEntity
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
    onRemoveEntity: (MediaControlSelectedEntity) -> Unit,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = HADimens.SPACE4),
                    contentAlignment = Alignment.Center,
                ) {
                    HALoading()
                }
            }
        } else {
            val hasMultiServer = uiState.serversDropdownItems.size > 1
            if (hasMultiServer) {
                item(key = "server_dropdown") {
                    ServerSelector(
                        items = uiState.serversDropdownItems,
                        selectedServerId = uiState.selectedServerId,
                        onServerSelected = onServerSelected,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "entity_picker") {
                EntityPickerSection(
                    availableEntities = uiState.availableEntities,
                    onEntitySelected = onEntitySelected,
                    modifier = Modifier.animateItem(),
                )
            }

            items(
                items = uiState.selectedEntities,
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
}

@Composable
private fun LazyItemScope.DescriptionSection() {
    val colorScheme = LocalHAColorScheme.current
    Text(
        text = stringResource(commonR.string.media_control_description),
        style = HATextStyle.Body,
        color = colorScheme.colorTextPrimary,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(horizontal = HADimens.SPACE4),
    )
    Spacer(modifier = Modifier.size(HADimens.SPACE4))
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
        Spacer(modifier = Modifier.size(HADimens.SPACE2))
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
        addButtonText = stringResource(commonR.string.media_control_select_entity),
        modifier = modifier.padding(horizontal = HADimens.SPACE4),
    )
}

@Composable
private fun ConfiguredEntityRow(
    item: MediaControlSelectedEntity,
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
            )

            val subtitle = listOfNotNull(
                item.serverName.takeIf { hasMultiServer },
                item.entityForDisplay?.subtitle(),
            ).takeIf { it.isNotEmpty() }?.joinToString(entitySubtitleSeparator())

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
            icon = Mdi.Close.rememberImageVector(),
            onClick = onRemove,
            contentDescription = stringResource(commonR.string.media_control_remove_entity),
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
                mediaControlEntityConfigs = listOf(
                    MediaControlEntityConfig(serverId = 1, entityId = "media_player.living_room"),
                    MediaControlEntityConfig(serverId = 1, entityId = "media_player.bedroom"),
                ),
            ),
            onServerSelected = {},
            onEntitySelected = {},
            onRemoveEntity = {},
        )
    }
}
