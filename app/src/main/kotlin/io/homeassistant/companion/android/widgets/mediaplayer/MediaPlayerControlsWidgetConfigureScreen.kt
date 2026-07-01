package io.homeassistant.companion.android.widgets.mediaplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2

/**
 * Stateful entry point that connects [MediaPlayerControlsWidgetConfigureViewModel] to the stateless
 * [MediaPlayerControlsWidgetConfigureContent]. Collects the UI state and surfaces one-shot user
 * messages as a Snackbar.
 */
@Composable
internal fun MediaPlayerControlsWidgetConfigureScreen(
    viewModel: MediaPlayerControlsWidgetConfigureViewModel,
    dynamicColorAvailable: Boolean,
    onActionClick: () -> Unit,
    onClose: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    uiState.userMessage?.let { messageResId ->
        val message = stringResource(messageResId)
        LaunchedEffect(messageResId) {
            snackbarHostState.showSnackbar(message)
            viewModel.onUserMessageShown()
        }
    }

    MediaPlayerControlsWidgetConfigureContent(
        uiState = uiState,
        dynamicColorAvailable = dynamicColorAvailable,
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
        onClose = onClose,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless configuration screen for the Media Player Controls widget.
 */
@Composable
internal fun MediaPlayerControlsWidgetConfigureContent(
    uiState: MediaPlayerControlsWidgetConfigureUiState,
    dynamicColorAvailable: Boolean,
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val state = uiState.config

    Scaffold(
        modifier = modifier,
        topBar = {
            HATopBar(
                title = { Text(stringResource(commonR.string.select_entity_to_display)) },
                onCloseClick = onClose,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                servers = uiState.servers,
                selectedServerId = state.selectedServerId,
                isUpdateWidget = state.isUpdateWidget,
                onServerSelected = onServerSelected,
            )

            EntityPickerSection(
                availableEntities = uiState.availableEntities,
                selectedEntities = uiState.selectedEntities,
                entityRegistry = uiState.entityRegistry,
                deviceRegistry = uiState.deviceRegistry,
                areaRegistry = uiState.areaRegistry,
                onEntityAdded = onEntityAdded,
                onEntityRemoved = onEntityRemoved,
            )

            MediaControlsOptions(
                state = state,
                onShowVolumeChanged = onShowVolumeChanged,
                onShowSkipChanged = onShowSkipChanged,
                onShowSeekChanged = onShowSeekChanged,
                onShowSourceChanged = onShowSourceChanged,
                modifier = Modifier.formControlWidth(),
            )

            HATextField(
                value = state.label,
                onValueChange = onLabelChanged,
                label = { Text(stringResource(commonR.string.label_label)) },
                maxLines = 1,
                modifier = Modifier.formControlWidth(),
            )

            BackgroundTypeDropdown(
                selected = state.backgroundType,
                dynamicColorAvailable = dynamicColorAvailable,
                onSelected = onBackgroundTypeSelected,
            )

            HAAccentButton(
                text = stringResource(
                    if (state.isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget,
                ),
                onClick = onActionClick,
                enabled = uiState.isInputValid,
                modifier = Modifier.formControlWidth(),
            )
        }
    }
}

@Composable
private fun ServerSelector(
    servers: List<Server>,
    selectedServerId: Int,
    isUpdateWidget: Boolean,
    onServerSelected: (Int) -> Unit,
) {
    if (servers.size <= 1 && !(isUpdateWidget && servers.none { it.id == selectedServerId })) return

    HADropdownMenu(
        items = servers.map { HADropdownItem(key = it.id, label = it.friendlyName) },
        selectedKey = selectedServerId,
        onItemSelected = onServerSelected,
        label = stringResource(commonR.string.widget_spinner_server),
        // When editing a widget whose persisted server no longer exists, the selected id is absent
        // from items; show a prompt instead of a blank field.
        placeholder = stringResource(commonR.string.select),
        modifier = Modifier.formControlWidth(),
    )
}

@Composable
private fun EntityPickerSection(
    availableEntities: List<Entity>,
    selectedEntities: List<SelectedMediaPlayer>,
    entityRegistry: List<EntityRegistryResponse>?,
    deviceRegistry: List<DeviceRegistryResponse>?,
    areaRegistry: List<AreaRegistryResponse>?,
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
            entities = availableEntities,
            selectedEntityId = null,
            onEntitySelectedId = onEntityAdded,
            onEntityCleared = {},
            modifier = Modifier.fillMaxWidth(),
            entityRegistry = entityRegistry,
            deviceRegistry = deviceRegistry,
            areaRegistry = areaRegistry,
        )

        if (selectedEntities.isNotEmpty()) {
            Text(
                text = stringResource(commonR.string.widget_media_selected_entities),
                style = HATextStyle.BodyMedium,
                color = LocalHAColorScheme.current.colorTextPrimary,
            )

            selectedEntities.forEach { entity ->
                SelectedEntityRow(
                    entityName = entity.friendlyName,
                    entityId = entity.entityId,
                    onRemove = { onEntityRemoved(entity.entityId) },
                )
            }

            // Delimiter so it is clear the rows above are the current selection.
            HAHorizontalDivider()
        }
    }
}

@Composable
private fun MediaControlsOptions(
    state: MediaPlayerControlsWidgetConfigureViewState,
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
private fun SelectedEntityRow(entityName: String, entityId: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entityName,
                style = HATextStyle.Body,
                color = LocalHAColorScheme.current.colorTextPrimary,
            )
            Text(
                text = entityId,
                style = HATextStyle.Body,
                color = LocalHAColorScheme.current.colorTextSecondary,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = stringResource(commonR.string.delete),
            )
        }
    }
}

@Composable
private fun BackgroundTypeDropdown(
    selected: WidgetBackgroundType,
    dynamicColorAvailable: Boolean,
    onSelected: (WidgetBackgroundType) -> Unit,
) {
    // The available options only depend on dynamic-color support, so remember the list instead of
    // rebuilding it on every recomposition. Shares the option set with the entity widget (#7007).
    val items = remember(dynamicColorAvailable) {
        buildList {
            if (dynamicColorAvailable) {
                add(WidgetBackgroundType.DYNAMICCOLOR)
            }
            add(WidgetBackgroundType.DAYNIGHT)
            add(WidgetBackgroundType.TRANSPARENT)
        }
    }

    HADropdownMenu(
        items = items.map { type ->
            HADropdownItem(key = type, label = stringResource(type.labelRes))
        },
        selectedKey = selected,
        onItemSelected = onSelected,
        label = stringResource(commonR.string.widget_background_type_label),
        modifier = Modifier.formControlWidth(),
    )
}

private val WidgetBackgroundType.labelRes: Int
    get() = when (this) {
        WidgetBackgroundType.DYNAMICCOLOR -> commonR.string.widget_background_type_dynamiccolor
        WidgetBackgroundType.DAYNIGHT -> commonR.string.widget_background_type_daynight
        WidgetBackgroundType.TRANSPARENT -> commonR.string.widget_background_type_transparent
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
            uiState = MediaPlayerControlsWidgetConfigureUiState(
                config = MediaPlayerControlsWidgetConfigureViewState(
                    selectedServerId = previewServer1.id,
                    selectedEntityIds = listOf(previewEntity1.entityId, previewEntity2.entityId),
                    label = "Living room",
                    showVolume = true,
                    showSkip = true,
                    showSeek = false,
                    showSource = true,
                    backgroundType = WidgetBackgroundType.DAYNIGHT,
                    isUpdateWidget = false,
                ),
                servers = listOf(previewServer1, previewServer2),
                availableEntities = emptyList(),
                selectedEntities = listOf(
                    SelectedMediaPlayer(previewEntity1.entityId, "Living room speaker"),
                    SelectedMediaPlayer(previewEntity2.entityId, "Kitchen speaker"),
                ),
                isInputValid = true,
            ),
            dynamicColorAvailable = true,
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
            onClose = {},
        )
    }
}
