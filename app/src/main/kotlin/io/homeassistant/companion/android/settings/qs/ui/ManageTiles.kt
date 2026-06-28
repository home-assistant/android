package io.homeassistant.companion.android.settings.qs.ui

import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.sharp.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.settings.qs.ManageTilesState
import io.homeassistant.companion.android.settings.qs.ManageTilesViewModel
import io.homeassistant.companion.android.settings.qs.TileSlot
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@VisibleForTesting
const val MANAGE_TILES_LABEL_TAG = "manage_tiles_label"

@VisibleForTesting
const val MANAGE_TILES_SUBTITLE_TAG = "manage_tiles_subtitle"

@VisibleForTesting
const val MANAGE_TILES_SERVER_DROPDOWN_TAG = "manage_tiles_server_dropdown"

@VisibleForTesting
const val MANAGE_TILES_ICON_BUTTON_TAG = "manage_tiles_icon_button"

@VisibleForTesting
const val MANAGE_TILES_RESET_ICON_TAG = "manage_tiles_reset_icon"

@VisibleForTesting
const val MANAGE_TILES_VIBRATE_SWITCH_TAG = "manage_tiles_vibrate_switch"

@VisibleForTesting
const val MANAGE_TILES_AUTH_SWITCH_TAG = "manage_tiles_auth_switch"

@VisibleForTesting
const val MANAGE_TILES_SUBMIT_TAG = "manage_tiles_submit"

@Composable
fun ManageTiles(
    viewModel: ManageTilesViewModel,
    onShowIconDialog: (tag: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val submitEnabled by viewModel.submitEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.tileInfoSnackbar.onEach {
            if (it != 0) {
                snackbarHostState.showSnackbar(context.getString(it))
            }
        }.launchIn(this)
    }

    ManageTiles(
        snackbarHostState = snackbarHostState,
        state = state,
        submitEnabled = submitEnabled,
        onTileSelected = viewModel::selectTile,
        onServerSelected = viewModel::selectServerId,
        onTileLabelChange = viewModel::setTileLabel,
        onTileSubtitleChange = viewModel::setTileSubtitle,
        onEntitySelectedId = viewModel::selectEntityId,
        onEntityCleared = { viewModel.selectEntityId("") },
        onShowIconDialog = { onShowIconDialog(state.selectedTile.id) },
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
    submitEnabled:Boolean,
    onTileSelected: (index: Int) -> Unit,
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
        contentWindowInsets = WindowInsets(),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(scrollState)
                .fillMaxWidth()
                .wrapContentWidth()
                .padding(all = HADimens.SPACE4)
                .widthIn(max = MaxButtonWidth)
                .padding(safeBottomPaddingValues(applyHorizontal = false)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TileLabelContent(
                state = state,
                onTileSelected = onTileSelected,
                onTileLabelChange = onTileLabelChange,
                onTileSubtitleChange = onTileSubtitleChange,
            )

            if (state.showServerSelector) {
                ServerDropdown(
                    servers = state.servers,
                    selectedServerId = state.selectedServerId,
                    onServerSelected = onServerSelected,
                    modifier = Modifier
                        .padding(top = HADimens.SPACE4)
                        .fillMaxWidth()
                        .testTag(MANAGE_TILES_SERVER_DROPDOWN_TAG),
                )
            }

            TileIconContent(
                state = state,
                onEntityCleared = onEntityCleared,
                onEntitySelectedId = onEntitySelectedId,
                onAuthRequiredChange = onAuthRequiredChange,
                onShowIconDialog = onShowIconDialog,
                onResetIcon = onResetIcon,
                onShouldVibrateChange = onShouldVibrateChange
            )

            HAFilledButton(
                text = stringResource(state.submitButtonLabel),
                onClick = onSubmit,
                enabled = submitEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HADimens.SPACE4)
                    .testTag(MANAGE_TILES_SUBMIT_TAG),
            )
        }
    }
}

@Composable
private fun TileLabelContent(
    state: ManageTilesState,
    onTileSelected: (index: Int) -> Unit,
    onTileLabelChange: (String) -> Unit,
    onTileSubtitleChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        TileDropdown(
            tileSlots = state.tileSlots,
            selectedTile = state.selectedTile,
            onTileSelected = onTileSelected,
            modifier = Modifier.fillMaxWidth(),
        )

        HAHorizontalDivider(modifier = Modifier.padding(vertical = HADimens.SPACE4))

        HATextField(
            value = state.tileLabel,
            onValueChange = onTileLabelChange,
            label = { Text(text = stringResource(R.string.tile_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MANAGE_TILES_LABEL_TAG),
        )

        if (state.showSubtitle && state.tileSubtitle != null) {
            HATextField(
                value = state.tileSubtitle,
                onValueChange = onTileSubtitleChange,
                label = { Text(text = stringResource(R.string.tile_subtitle)) },
                modifier = Modifier
                    .padding(top = HADimens.SPACE4)
                    .fillMaxWidth()
                    .testTag(MANAGE_TILES_SUBTITLE_TAG),
            )
        }
    }
}

@Composable
private fun TileIconContent(
    state: ManageTilesState,
    onEntityCleared: () -> Unit,
    onEntitySelectedId: (String) -> Unit,
    onShowIconDialog: () -> Unit,
    onResetIcon: () -> Unit,
    onShouldVibrateChange: (Boolean) -> Unit,
    onAuthRequiredChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
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
}

@Composable
private fun TileDropdown(
    tileSlots: List<TileSlot>,
    selectedTile: TileSlot,
    onTileSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    HADropdownMenu(
        items = tileSlots.mapIndexed { index, slot -> HADropdownItem(key = index, label = slot.name) },
        selectedKey = tileSlots.indexOf(selectedTile).takeIf { it >= 0 },
        onItemSelected = onTileSelected,
        label = stringResource(R.string.tile_select),
        modifier = modifier,
    )
}

@Composable
private fun ServerDropdown(
    servers: List<Server>,
    selectedServerId: Int,
    onServerSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    HADropdownMenu(
        items = servers.map { HADropdownItem(key = it.id, label = it.friendlyName) },
        selectedKey = selectedServerId,
        onItemSelected = onServerSelected,
        label = stringResource(R.string.tile_server),
        modifier = modifier,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.tile_icon),
            style = HATextStyle.Body,
            modifier = Modifier.padding(end = HADimens.SPACE2),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2)
        ) {
            if (showResetIcon) {
                HAIconButton(
                    modifier = Modifier
                        .padding(start = HADimens.SPACE1),
                    icon = Icons.Default.Restore,
                    onClick = onResetIcon,
                    contentDescription = MANAGE_TILES_RESET_ICON_TAG
                )
            }
            OutlinedButton(
                onClick = onShowIconDialog,
                modifier = Modifier.testTag(MANAGE_TILES_ICON_BUTTON_TAG),
            ) {
                if (selectedIcon != null) {
                    com.mikepenz.iconics.compose.Image(
                        selectedIcon,
                        contentDescription = stringResource(R.string.tile_icon),
                        colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorFillPrimaryLoudResting),
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.select),
                        style = HATextStyle.BodyMedium,
                        color = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
                    )
                }
            }
        }
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
                selectedTile = previewState.tileSlots[1],
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
        TileSlot(id = "tile_1", name = "Tile 1"),
        TileSlot(id = "tile_2", name = "Tile 2"),
    ),
    selectedTile = TileSlot(id = "tile_1", name = "Tile 1"),
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
