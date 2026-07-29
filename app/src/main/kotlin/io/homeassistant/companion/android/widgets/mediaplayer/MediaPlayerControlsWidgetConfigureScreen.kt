package io.homeassistant.companion.android.widgets.mediaplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState.Loaded
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.widgets.WidgetBackgroundTypeDropdown

/**
 * Stateful entry point that connects [MediaPlayerControlsWidgetConfigureViewModel] to the stateless
 * [MediaPlayerControlsWidgetConfigureContent]. Collects the UI state and surfaces one-shot user
 * messages as a Snackbar.
 */
@Composable
internal fun MediaPlayerControlsWidgetConfigureScreen(
    viewModel: MediaPlayerControlsWidgetConfigureViewModel,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onActionClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.errors.collect { resId ->
            snackbarHostState.showSnackbar(resources.getString(resId))
        }
    }

    MediaPlayerControlsWidgetConfigureContent(
        state = state,
        snackbarHostState = snackbarHostState,
        canNavigateBack = canNavigateBack,
        onNavigate = onNavigate,
        onServerSelected = viewModel::onServerSelected,
        onEntityAdded = viewModel::onEntityAdded,
        onEntityRemoved = viewModel::onEntityRemoved,
        onLabelChanged = viewModel::onLabelChanged,
        onShowVolumeChanged = viewModel::onShowVolumeChanged,
        onShowSkipChanged = viewModel::onShowSkipChanged,
        onShowSeekChanged = viewModel::onShowSeekChanged,
        onShowSourceChanged = viewModel::onShowSourceChanged,
        onBackgroundTypeSelected = viewModel::onBackgroundTypeSelected,
        onActionClick = onActionClick,
    )
}

/**
 * Stateless configuration screen for the Media Player Controls widget.
 */
@Composable
internal fun MediaPlayerControlsWidgetConfigureContent(
    state: MediaPlayerControlsWidgetConfigureState,
    canNavigateBack: Boolean,
    onNavigate: () -> Unit,
    onServerSelected: (Int) -> Unit,
    onEntityAdded: (String) -> Unit,
    onEntityRemoved: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onShowVolumeChanged: (Boolean) -> Unit,
    onShowSkipChanged: (Boolean) -> Unit,
    onShowSeekChanged: (Boolean) -> Unit,
    onShowSourceChanged: (Boolean) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    onActionClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            HATopBar(
                title = { Text(stringResource(commonR.string.select_entity_to_display)) },
                onBackClick = onNavigate.takeIf { canNavigateBack },
                onCloseClick = onNavigate.takeIf { !canNavigateBack },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(HADimens.SPACE4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            ServerSelector(
                items = state.serversDropdownItems,
                selectedServerId = state.selectedServerId,
                showServerSelector = state.showServerSelector,
                onServerSelected = onServerSelected,
            )

            EntityPickerSection(
                availableEntities = state.availableEntities,
                selectedEntities = state.selectedEntities,
                onEntityAdded = onEntityAdded,
                onEntityRemoved = onEntityRemoved,
            )

            ConfigurationSections(
                state = state,
                onLabelChanged = onLabelChanged,
                onShowVolumeChanged = onShowVolumeChanged,
                onShowSkipChanged = onShowSkipChanged,
                onShowSeekChanged = onShowSeekChanged,
                onShowSourceChanged = onShowSourceChanged,
                onBackgroundTypeSelected = onBackgroundTypeSelected,
            )

            HAAccentButton(
                text = stringResource(
                    if (state.isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget,
                ),
                onClick = onActionClick,
                enabled = state.isActionEnabled,
                modifier = Modifier.formControlWidth(),
            )
        }
    }
}

@Composable
private fun ColumnScope.ConfigurationSections(
    state: MediaPlayerControlsWidgetConfigureState,
    onLabelChanged: (String) -> Unit,
    onShowVolumeChanged: (Boolean) -> Unit,
    onShowSkipChanged: (Boolean) -> Unit,
    onShowSeekChanged: (Boolean) -> Unit,
    onShowSourceChanged: (Boolean) -> Unit,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
) {
    AnimatedVisibility(visible = state.showConfiguration) {
        Column(
            modifier = Modifier.formControlWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            MediaControlsOptions(
                state = state,
                onShowVolumeChanged = onShowVolumeChanged,
                onShowSkipChanged = onShowSkipChanged,
                onShowSeekChanged = onShowSeekChanged,
                onShowSourceChanged = onShowSourceChanged,
            )

            HATextField(
                value = state.label,
                onValueChange = onLabelChanged,
                label = { Text(stringResource(commonR.string.label_label)) },
                maxLines = 1,
            )

            WidgetBackgroundTypeDropdown(
                selected = state.selectedBackgroundType,
                dynamicColorAvailable = state.dynamicColorAvailable,
                onSelected = onBackgroundTypeSelected,
            )
        }
    }
}

@Composable
private fun ServerSelector(
    items: List<HADropdownItem<Int>>,
    selectedServerId: Int,
    showServerSelector: Boolean,
    onServerSelected: (Int) -> Unit,
) {
    if (!showServerSelector) return

    HADropdownMenu(
        items = items,
        selectedKey = selectedServerId,
        onItemSelected = onServerSelected,
        label = stringResource(commonR.string.server_select),
        placeholder = stringResource(commonR.string.server_select),
        modifier = Modifier.formControlWidth(),
        enabled = items.isNotEmpty(),
    )
}

@Composable
private fun EntityPickerSection(
    availableEntities: EntityDisplayState<EntityDisplayWithContext>,
    selectedEntities: List<EntityDisplayWithContext>,
    onEntityAdded: (String) -> Unit,
    onEntityRemoved: (String) -> Unit,
) {
    Column(
        modifier = Modifier.formControlWidth(),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        // The picker acts as an "add entity" control; selected entities are listed below so a
        // widget can control several media players (it shows whichever one is currently playing).
        EntityPicker(
            displayState = availableEntities,
            selectedEntityId = null,
            onSelectionChanged = { entityId ->
                if (entityId != null) {
                    onEntityAdded(entityId)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (selectedEntities.isNotEmpty()) {
            Text(
                text = stringResource(commonR.string.widget_media_selected_entities),
                style = HATextStyle.BodyMedium,
                color = LocalHAColorScheme.current.colorTextPrimary,
            )

            selectedEntities.forEach { entity ->
                SelectedEntityRow(
                    entity = entity,
                    onRemove = { onEntityRemoved(entity.entityId) },
                )
            }

            HAHorizontalDivider()
        }
    }
}

@Composable
private fun MediaControlsOptions(
    state: MediaPlayerControlsWidgetConfigureState,
    onShowVolumeChanged: (Boolean) -> Unit,
    onShowSkipChanged: (Boolean) -> Unit,
    onShowSeekChanged: (Boolean) -> Unit,
    onShowSourceChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
    ) {
        CheckboxRow(
            text = stringResource(commonR.string.widget_media_show_volume),
            checked = state.showVolume,
            onCheckedChange = onShowVolumeChanged,
        )
        CheckboxRow(
            text = stringResource(commonR.string.widget_media_show_skip),
            checked = state.showSkip,
            onCheckedChange = onShowSkipChanged,
        )
        CheckboxRow(
            text = stringResource(commonR.string.widget_media_show_seek),
            checked = state.showSeek,
            onCheckedChange = onShowSeekChanged,
        )
        CheckboxRow(
            text = stringResource(commonR.string.widget_media_show_source),
            checked = state.showSource,
            onCheckedChange = onShowSourceChanged,
        )
    }
}

@Composable
private fun CheckboxRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                indication = null,
                interactionSource = null,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        HACheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = text,
            style = HATextStyle.Body,
            color = LocalHAColorScheme.current.colorTextPrimary,
        )
    }
}

@Composable
private fun SelectedEntityRow(entity: EntityDisplayWithContext, onRemove: () -> Unit) {
    val colorScheme = LocalHAColorScheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.name,
                style = HATextStyle.Body,
                color = colorScheme.colorTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            entity.subtitle(LocalLayoutDirection.current)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = HATextStyle.BodyMedium,
                    color = colorScheme.colorTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = HADimens.SPACE1),
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Clear,
                tint = colorScheme.colorOnNeutralNormal,
                contentDescription = stringResource(commonR.string.delete),
            )
        }
    }
}

/** Caps form controls at a readable width and centres them on large screens, matching #7007. */
private fun Modifier.formControlWidth(): Modifier = this
    .widthIn(max = MaxButtonWidth)
    .fillMaxWidth()

@Preview
@Composable
private fun MediaPlayerControlsWidgetConfigureContentPreview() {
    HAThemeForPreview {
        MediaPlayerControlsWidgetConfigureContent(
            state = MediaPlayerControlsWidgetConfigureState(
                selectedServerId = previewServer1.id,
                entityDisplayState = Loaded(
                    listOf(
                        EntityDisplayWithContext(EntityDisplayWithoutContext(previewEntity1), areaName = "Kitchen"),
                        EntityDisplayWithContext(EntityDisplayWithoutContext(previewEntity2)),
                    ),
                ),
                selectedEntityIds = listOf(previewEntity1.entityId, previewEntity2.entityId),
                label = "Living room",
                showVolume = true,
                showSkip = true,
                showSeek = false,
                showSource = true,
                selectedBackgroundType = WidgetBackgroundType.DAYNIGHT,
                dynamicColorAvailable = true,
                isUpdateWidget = false,
                serversDropdownItems = listOf(previewServer1, previewServer2).map {
                    HADropdownItem(key = it.id, label = it.friendlyName)
                },
            ),
            onServerSelected = {},
            onEntityAdded = {},
            onEntityRemoved = {},
            onLabelChanged = {},
            onShowVolumeChanged = {},
            onShowSkipChanged = {},
            onShowSeekChanged = {},
            onShowSourceChanged = {},
            onBackgroundTypeSelected = {},
            onActionClick = {},
            onNavigate = {},
            canNavigateBack = true,
        )
    }
}
