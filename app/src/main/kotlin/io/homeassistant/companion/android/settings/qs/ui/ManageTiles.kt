package io.homeassistant.companion.android.settings.qs.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
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
import io.homeassistant.companion.android.settings.qs.ManageTilesViewModel
import io.homeassistant.companion.android.settings.qs.TileSlot
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Stable
internal data class ManageTilesState(
    val tileSlots: List<TileSlot>,
    val selectedTile: TileSlot,
    val servers: List<Server>,
    val selectedServerId: Int,
    val showServerSelector: Boolean,
    val tileLabel: String,
    val showSubtitle: Boolean,
    val tileSubtitle: String,
    val entities: List<Entity>,
    val selectedEntityId: String,
    val entityRegistry: List<EntityRegistryResponse>,
    val deviceRegistry: List<DeviceRegistryResponse>,
    val areaRegistry: List<AreaRegistryResponse>,
    val selectedIcon: IIcon?,
    val showResetIcon: Boolean,
    val shouldVibrate: Boolean,
    val authRequired: Boolean,
    val submitButtonLabel: Int,
    val submitEnabled: Boolean,
)

@Composable
fun ManageTiles(
    viewModel: ManageTilesViewModel,
    onShowIconDialog: (tag: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.tileInfoSnackbar.onEach {
            if (it != 0) {
                snackbarHostState.showSnackbar(context.getString(it))
            }
        }.launchIn(this)
    }

    val state = ManageTilesState(
        tileSlots = viewModel.slots,
        selectedTile = viewModel.selectedTile,
        servers = viewModel.servers,
        selectedServerId = viewModel.selectedServerId,
        showServerSelector = viewModel.servers.size > 1 ||
            viewModel.servers.none { it.id == viewModel.selectedServerId },
        tileLabel = viewModel.tileLabel,
        showSubtitle = SdkVersion.isAtLeast(Build.VERSION_CODES.Q),
        tileSubtitle = viewModel.tileSubtitle.orEmpty(),
        entities = viewModel.sortedEntities,
        selectedEntityId = viewModel.selectedEntityId,
        entityRegistry = viewModel.entityRegistry,
        deviceRegistry = viewModel.deviceRegistry,
        areaRegistry = viewModel.areaRegistry,
        selectedIcon = viewModel.selectedIcon,
        showResetIcon = viewModel.selectedIconId != null && viewModel.selectedEntityId.isNotBlank(),
        shouldVibrate = viewModel.selectedShouldVibrate,
        authRequired = viewModel.tileAuthRequired,
        submitButtonLabel = viewModel.submitButtonLabel,
        submitEnabled = viewModel.tileLabel.isNotBlank() &&
            viewModel.selectedServerId in viewModel.servers.map { it.id } &&
            viewModel.selectedEntityId in viewModel.sortedEntities.map { it.entityId },
    )

    ManageTiles(
        snackbarHostState = snackbarHostState,
        state = state,
        onTileSelected = viewModel::selectTile,
        onServerSelected = viewModel::selectServerId,
        onTileLabelChange = { viewModel.tileLabel = it },
        onTileSubtitleChange = { viewModel.tileSubtitle = it },
        onEntitySelectedId = viewModel::selectEntityId,
        onEntityCleared = { viewModel.selectEntityId("") },
        onShowIconDialog = { onShowIconDialog(viewModel.selectedTile.id) },
        onResetIcon = { viewModel.selectIcon(null) },
        onShouldVibrateChange = { viewModel.selectedShouldVibrate = it },
        onAuthRequiredChange = { viewModel.tileAuthRequired = it },
        onSubmit = viewModel::addTile,
        modifier = modifier,
    )
}

@Composable
internal fun ManageTiles(
    snackbarHostState: SnackbarHostState,
    state: ManageTilesState,
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
        contentWindowInsets = WindowInsets()
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxWidth()
                .verticalScroll(scrollState)
            ,
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(safeBottomPaddingValues(applyHorizontal = false))
                    .padding(all = HADimens.SPACE4)
                    .widthIn(max = MaxButtonWidth)
                ,
                horizontalAlignment = Alignment.CenterHorizontally
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
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.showSubtitle) {
                    HATextField(
                        value = state.tileSubtitle,
                        onValueChange = onTileSubtitleChange,
                        label = { Text(text = stringResource(R.string.tile_subtitle)) },
                        modifier = Modifier
                            .padding(top = HADimens.SPACE4)
                            .fillMaxWidth()
                    )
                }

                if (state.showServerSelector) {
                    ServerDropdown(
                        servers = state.servers,
                        selectedServerId = state.selectedServerId,
                        onServerSelected = onServerSelected,
                        modifier = Modifier
                            .padding(top = HADimens.SPACE4)
                            .fillMaxWidth(),
                    )
                }

                EntityPicker(
                    entities = state.entities,
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
                    checked = state.shouldVibrate,
                    onCheckedChange = onShouldVibrateChange,
                )

                LabeledSwitchRow(
                    label = stringResource(R.string.tile_auth_required),
                    checked = state.authRequired,
                    onCheckedChange = onAuthRequiredChange,
                )

                HAFilledButton(
                    text = stringResource(state.submitButtonLabel),
                    onClick = onSubmit,
                    enabled = state.submitEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = HADimens.SPACE4),
                )
            }
        }
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
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.tile_icon),
            style = HATextStyle.Body,
            modifier = Modifier.padding(end = HADimens.SPACE2),
        )

        OutlinedButton(
            onClick = onShowIconDialog
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
    if (showResetIcon) {
        HAPlainButton(
            text = stringResource(R.string.tile_icon_original),
            onClick = onResetIcon,
            modifier = Modifier.padding(start = HADimens.SPACE1),
        )
    }
}

@Composable
private fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
            state = previewState.copy(
                selectedTile = previewState.tileSlots[1],
                tileLabel = "Living room",
                tileSubtitle = "Lights",
                selectedEntityId = "light.living_room",
                showResetIcon = true,
                shouldVibrate = true,
                submitButtonLabel = R.string.tile_save,
                submitEnabled = true,
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
    showServerSelector = false,
    tileLabel = "",
    showSubtitle = true,
    tileSubtitle = "",
    entities = emptyList(),
    selectedEntityId = "",
    entityRegistry = emptyList(),
    deviceRegistry = emptyList(),
    areaRegistry = emptyList(),
    selectedIcon = null,
    showResetIcon = false,
    shouldVibrate = false,
    authRequired = false,
    submitButtonLabel = R.string.tile_add,
    submitEnabled = false,
)
