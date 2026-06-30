package io.homeassistant.companion.android.settings.qs.ui

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.compose.theme.MaxButtonWidth
import io.homeassistant.companion.android.settings.qs.ManageTilesState
import io.homeassistant.companion.android.settings.qs.ManageTilesViewModel
import io.homeassistant.companion.android.settings.qs.TileSlot
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.icondialog.IconDialog
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@VisibleForTesting
const val MANAGE_TILES_VIBRATE_SWITCH_TAG = "manage_tiles_vibrate_switch"

@VisibleForTesting
const val MANAGE_TILES_AUTH_SWITCH_TAG = "manage_tiles_auth_switch"

/**
 * VM-facing overload of ManageTiles. Owns the icon dialog lifecycle — showing/dismissing
 * [IconDialog] and forwarding icon selections to the ViewModel.
 */
@Composable
internal fun ManageTiles(viewModel: ManageTilesViewModel, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showIconDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.tileInfoSnackbar.onEach { resId ->
            snackbarHostState.showSnackbar(context.getString(resId))
        }.launchIn(this)
    }

    if (showIconDialog) {
        IconDialog(
            onSelect = { icon ->
                viewModel.selectIcon(icon)
                showIconDialog = false
            },
            onDismissRequest = { showIconDialog = false },
        )
    }

    ManageTiles(
        snackbarHostState = snackbarHostState,
        state = state,
        submitEnabled = state.submitEnabled,
        onTileSelected = viewModel::selectTile,
        onServerSelected = viewModel::selectServerId,
        onTileLabelChange = viewModel::setTileLabel,
        onTileSubtitleChange = viewModel::setTileSubtitle,
        onEntitySelectedId = viewModel::selectEntityId,
        onEntityCleared = { viewModel.selectEntityId("") },
        onShowIconDialog = { showIconDialog = true },
        onResetIcon = { viewModel.selectIcon(null) },
        onShouldVibrateChange = viewModel::setShouldVibrate,
        onAuthRequiredChange = viewModel::setAuthRequired,
        onSubmit = viewModel::addTile,
        modifier = modifier,
    )
}

@Composable
internal fun ManageTiles(
    snackbarHostState: SnackbarHostState,
    state: ManageTilesState,
    submitEnabled: Boolean,
    onTileSelected: (id: String) -> Unit,
    onServerSelected: (Int) -> Unit,
    onTileLabelChange: (String) -> Unit,
    onTileSubtitleChange: (String) -> Unit,
    onEntitySelectedId: (String) -> Unit,
    onEntityCleared: () -> Unit,
    onShowIconDialog: () -> Unit,
    onResetIcon: () -> Unit,
    onShouldVibrateChange: (Boolean) -> Unit,
    onAuthRequiredChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(safeBottomWindowInsets(applyHorizontal = false)),
            )
        },
        contentWindowInsets = safeBottomWindowInsets(applyHorizontal = false),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .wrapContentWidth()
                .padding(all = HADimens.SPACE4)
                .widthIn(max = MaxButtonWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE4),
        ) {
            TileLabelContent(
                state = state,
                onTileSelected = onTileSelected,
                onTileLabelChange = onTileLabelChange,
                onTileSubtitleChange = onTileSubtitleChange,
            )

            if (state.showServerSelector) {
                HADropdownMenu(
                    items = state.serversDropdownItems,
                    selectedKey = state.selectedServerId,
                    onItemSelected = onServerSelected,
                    label = stringResource(R.string.tile_server),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TileConfigContent(
                state = state,
                onEntityCleared = onEntityCleared,
                onEntitySelectedId = onEntitySelectedId,
                onAuthRequiredChange = onAuthRequiredChange,
                onShowIconDialog = onShowIconDialog,
                onResetIcon = onResetIcon,
                onShouldVibrateChange = onShouldVibrateChange,
            )

            HAFilledButton(
                text = stringResource(state.submitButtonLabel),
                onClick = onSubmit,
                enabled = submitEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColumnScope.TileLabelContent(
    state: ManageTilesState,
    onTileSelected: (id: String) -> Unit,
    onTileLabelChange: (String) -> Unit,
    onTileSubtitleChange: (String) -> Unit,
) {
    HADropdownMenu(
        items = state.tileSlotsDropdownItems,
        selectedKey = state.selectedTileId,
        onItemSelected = onTileSelected,
        label = stringResource(R.string.tile_select),
        modifier = Modifier.fillMaxWidth(),
    )

    HAHorizontalDivider(modifier = Modifier.padding(vertical = HADimens.SPACE4))

    HATextField(
        value = state.tileLabel,
        onValueChange = onTileLabelChange,
        label = { Text(text = stringResource(R.string.tile_label)) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.showSubtitle && state.tileSubtitle != null) {
        HATextField(
            value = state.tileSubtitle,
            onValueChange = onTileSubtitleChange,
            label = { Text(text = stringResource(R.string.tile_subtitle)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.TileConfigContent(
    state: ManageTilesState,
    onEntityCleared: () -> Unit,
    onEntitySelectedId: (String) -> Unit,
    onShowIconDialog: () -> Unit,
    onResetIcon: () -> Unit,
    onShouldVibrateChange: (Boolean) -> Unit,
    onAuthRequiredChange: (Boolean) -> Unit,
) {
    EntityPicker(
        entities = state.sortedEntities,
        selectedEntityId = state.selectedEntityId,
        onEntitySelectedId = onEntitySelectedId,
        onEntityCleared = onEntityCleared,
        modifier = Modifier.padding(vertical = HADimens.SPACE4),
        addButtonText = stringResource(R.string.tile_entity),
        entityRegistry = state.entityRegistry,
        deviceRegistry = state.deviceRegistry,
        areaRegistry = state.areaRegistry,
    )

    TileIconRow(
        selectedIcon = state.selectedIcon,
        showResetIcon = state.showResetIcon,
        onShowIconDialog = onShowIconDialog,
        onResetIcon = onResetIcon,
    )

    LabeledSwitchRow(
        label = stringResource(R.string.tile_vibrate),
        checked = state.selectedShouldVibrate,
        onCheckedChange = onShouldVibrateChange,
        switchTestTag = MANAGE_TILES_VIBRATE_SWITCH_TAG,
    )

    LabeledSwitchRow(
        label = stringResource(R.string.tile_auth_required),
        checked = state.tileAuthRequired,
        onCheckedChange = onAuthRequiredChange,
        switchTestTag = MANAGE_TILES_AUTH_SWITCH_TAG,
    )
}

@Composable
private fun TileIconRow(
    selectedIcon: IIcon?,
    showResetIcon: Boolean,
    onShowIconDialog: () -> Unit,
    onResetIcon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconContentDescription = stringResource(R.string.tile_icon)
    val icon = selectedIcon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.tile_icon),
            style = HATextStyle.Body,
            modifier = Modifier.padding(end = HADimens.SPACE2),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showResetIcon) {
            HAIconButton(
                modifier = Modifier.padding(start = HADimens.SPACE1),
                icon = Icons.Default.Restore,
                onClick = onResetIcon,
                contentDescription = stringResource(R.string.tile_reset_icon),
            )
        }
        HAPlainButton(
            text = if (icon != null) "" else stringResource(R.string.select),
            onClick = onShowIconDialog,
            modifier = Modifier.semantics { contentDescription = iconContentDescription },
            prefix = if (icon != null) {
                {
                    Image(
                        icon,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorFillPrimaryLoudResting),
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    switchTestTag: String = "",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = HATextStyle.Body,
        )
        HASwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTestTag),
        )
    }
}

@Preview(name = "Add tile", showBackground = true)
@Composable
private fun ManageTilesPreview() {
    HAThemeForPreview {
        ManageTiles(
            snackbarHostState = remember { SnackbarHostState() },
            state = previewState,
            submitEnabled = false,
            onTileSelected = {},
            onServerSelected = {},
            onTileLabelChange = {},
            onTileSubtitleChange = {},
            onEntitySelectedId = {},
            onEntityCleared = {},
            onShowIconDialog = {},
            onResetIcon = {},
            onShouldVibrateChange = {},
            onAuthRequiredChange = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Update tile", showBackground = true)
@Composable
private fun ManageTilesUpdatePreview() {
    HAThemeForPreview {
        ManageTiles(
            snackbarHostState = remember { SnackbarHostState() },
            submitEnabled = false,
            state = previewState.copy(
                selectedTileId = "tile_2",
                tileLabel = "Living room",
                tileSubtitle = "Lights",
                selectedEntityId = "light.living_room",
                submitButtonLabel = R.string.tile_save,
            ),
            onTileSelected = {},
            onServerSelected = {},
            onTileLabelChange = {},
            onTileSubtitleChange = {},
            onEntitySelectedId = {},
            onEntityCleared = {},
            onShowIconDialog = {},
            onResetIcon = {},
            onShouldVibrateChange = {},
            onAuthRequiredChange = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Labeled switch row", showBackground = true)
@Composable
private fun LabeledSwitchRowPreview() {
    HAThemeForPreview {
        LabeledSwitchRow(
            label = "Vibrate when selected",
            checked = true,
            onCheckedChange = {},
        )
    }
}

private val previewState = ManageTilesState(
    tileSlots = listOf(
        TileSlot("tile_1", "Tile 1"),
        TileSlot("tile_2", "Tile 2"),
    ),
    selectedTileId = "tile_1",
    servers = emptyList(),
    selectedServerId = 0,
    tileLabel = "",
    tileSubtitle = "",
    selectedEntityId = "",
    entityRegistry = emptyList(),
    deviceRegistry = emptyList(),
    areaRegistry = emptyList(),
    selectedIcon = null,
    submitButtonLabel = R.string.tile_add,
)
