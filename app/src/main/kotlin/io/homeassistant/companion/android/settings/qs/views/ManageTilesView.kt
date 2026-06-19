package io.homeassistant.companion.android.settings.qs.views

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
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

@Composable
fun ManageTilesView(
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

    val showServerSelector = viewModel.servers.size > 1 ||
        viewModel.servers.none { it.id == viewModel.selectedServerId }
    val submitEnabled = viewModel.tileLabel.isNotBlank() &&
        viewModel.selectedServerId in viewModel.servers.map { it.id } &&
        viewModel.selectedEntityId in viewModel.sortedEntities.map { it.entityId }

    ManageTilesView(
        snackbarHostState = snackbarHostState,
        tileSlots = viewModel.slots,
        selectedTile = viewModel.selectedTile,
        onTileSelected = viewModel::selectTile,
        servers = viewModel.servers,
        selectedServerId = viewModel.selectedServerId,
        showServerSelector = showServerSelector,
        onServerSelected = viewModel::selectServerId,
        tileLabel = viewModel.tileLabel,
        onTileLabelChange = { viewModel.tileLabel = it },
        showSubtitle = SdkVersion.isAtLeast(Build.VERSION_CODES.Q),
        tileSubtitle = viewModel.tileSubtitle.orEmpty(),
        onTileSubtitleChange = { viewModel.tileSubtitle = it },
        entities = viewModel.sortedEntities,
        selectedEntityId = viewModel.selectedEntityId,
        onEntitySelectedId = viewModel::selectEntityId,
        onEntityCleared = { viewModel.selectEntityId("") },
        entityRegistry = viewModel.entityRegistry,
        deviceRegistry = viewModel.deviceRegistry,
        areaRegistry = viewModel.areaRegistry,
        selectedIcon = viewModel.selectedIcon,
        onShowIconDialog = { onShowIconDialog(viewModel.selectedTile.id) },
        showResetIcon = viewModel.selectedIconId != null && viewModel.selectedEntityId.isNotBlank(),
        onResetIcon = { viewModel.selectIcon(null) },
        shouldVibrate = viewModel.selectedShouldVibrate,
        onShouldVibrateChange = { viewModel.selectedShouldVibrate = it },
        authRequired = viewModel.tileAuthRequired,
        onAuthRequiredChange = { viewModel.tileAuthRequired = it },
        submitButtonLabel = viewModel.submitButtonLabel,
        submitEnabled = submitEnabled,
        onSubmit = viewModel::addTile,
        modifier = modifier,
    )
}

@Composable
internal fun ManageTilesView(
    snackbarHostState: SnackbarHostState,
    tileSlots: List<TileSlot>,
    selectedTile: TileSlot,
    onTileSelected: (index: Int) -> Unit,
    servers: List<Server>,
    selectedServerId: Int,
    showServerSelector: Boolean,
    onServerSelected: (Int) -> Unit,
    tileLabel: String,
    onTileLabelChange: (String) -> Unit,
    showSubtitle: Boolean,
    tileSubtitle: String,
    onTileSubtitleChange: (String) -> Unit,
    entities: List<Entity>,
    selectedEntityId: String,
    onEntitySelectedId: (String) -> Unit,
    onEntityCleared: () -> Unit,
    entityRegistry: List<EntityRegistryResponse>,
    deviceRegistry: List<DeviceRegistryResponse>,
    areaRegistry: List<AreaRegistryResponse>,
    selectedIcon: IIcon?,
    onShowIconDialog: () -> Unit,
    showResetIcon: Boolean,
    onResetIcon: () -> Unit,
    shouldVibrate: Boolean,
    onShouldVibrateChange: (Boolean) -> Unit,
    authRequired: Boolean,
    onAuthRequiredChange: (Boolean) -> Unit,
    submitButtonLabel: Int,
    submitEnabled: Boolean,
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
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(scrollState),
        ) {
            Column(
                modifier = Modifier
                    .padding(safeBottomPaddingValues(applyHorizontal = false))
                    .padding(16.dp),
            ) {
                HADropdownMenu(
                    items = tileSlots.mapIndexed { index, slot -> HADropdownItem(key = index, label = slot.name) },
                    selectedKey = tileSlots.indexOf(selectedTile).takeIf { it >= 0 },
                    onItemSelected = onTileSelected,
                    label = stringResource(R.string.tile_select),
                    modifier = Modifier.fillMaxWidth(),
                )

                HAHorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                HATextField(
                    value = tileLabel,
                    onValueChange = onTileLabelChange,
                    label = { Text(text = stringResource(R.string.tile_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (showSubtitle) {
                    HATextField(
                        value = tileSubtitle,
                        onValueChange = onTileSubtitleChange,
                        label = { Text(text = stringResource(R.string.tile_subtitle)) },
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(),
                    )
                }

                if (showServerSelector) {
                    HADropdownMenu(
                        items = servers.map { HADropdownItem(key = it.id, label = it.friendlyName) },
                        selectedKey = selectedServerId,
                        onItemSelected = onServerSelected,
                        label = stringResource(R.string.tile_server),
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(),
                    )
                }

                EntityPicker(
                    entities = entities,
                    selectedEntityId = selectedEntityId,
                    onEntitySelectedId = onEntitySelectedId,
                    onEntityCleared = onEntityCleared,
                    modifier = Modifier.padding(vertical = 16.dp),
                    addButtonText = stringResource(R.string.tile_entity),
                    entityRegistry = entityRegistry,
                    deviceRegistry = deviceRegistry,
                    areaRegistry = areaRegistry,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.tile_icon),
                        style = HATextStyle.Body,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    // The selected icon is a user-picked Material Design Icon (Iconics [IIcon]) rather
                    // than a static vector, so it is rendered directly inside a clickable button that
                    // opens the icon dialog instead of using HAIconButton (which only takes an ImageVector).
                    IconButton(onClick = onShowIconDialog) {
                        selectedIcon?.let { icon ->
                            com.mikepenz.iconics.compose.Image(
                                icon,
                                contentDescription = stringResource(R.string.tile_icon),
                                colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorFillPrimaryLoudResting),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    if (showResetIcon) {
                        HAPlainButton(
                            text = stringResource(R.string.tile_icon_original),
                            onClick = onResetIcon,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.tile_vibrate),
                        style = HATextStyle.Body,
                    )
                    HASwitch(
                        checked = shouldVibrate,
                        onCheckedChange = onShouldVibrateChange,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.tile_auth_required),
                        style = HATextStyle.Body,
                    )
                    HASwitch(
                        checked = authRequired,
                        onCheckedChange = onAuthRequiredChange,
                    )
                }

                HAFilledButton(
                    text = stringResource(submitButtonLabel),
                    onClick = onSubmit,
                    enabled = submitEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
        }
    }
}

@Preview(name = "Add tile", showBackground = true)
@Composable
private fun ManageTilesViewPreview() {
    HAThemeForPreview {
        ManageTilesView(
            snackbarHostState = remember { SnackbarHostState() },
            tileSlots = previewTileSlots,
            selectedTile = previewTileSlots.first(),
            onTileSelected = {},
            servers = emptyList(),
            selectedServerId = 0,
            showServerSelector = false,
            onServerSelected = {},
            tileLabel = "",
            onTileLabelChange = {},
            showSubtitle = true,
            tileSubtitle = "",
            onTileSubtitleChange = {},
            entities = emptyList(),
            selectedEntityId = "",
            onEntitySelectedId = {},
            onEntityCleared = {},
            entityRegistry = emptyList(),
            deviceRegistry = emptyList(),
            areaRegistry = emptyList(),
            selectedIcon = null,
            onShowIconDialog = {},
            showResetIcon = false,
            onResetIcon = {},
            shouldVibrate = false,
            onShouldVibrateChange = {},
            authRequired = false,
            onAuthRequiredChange = {},
            submitButtonLabel = R.string.tile_add,
            submitEnabled = false,
            onSubmit = {},
        )
    }
}

@Preview(name = "Update tile", showBackground = true)
@Composable
private fun ManageTilesViewUpdatePreview() {
    HAThemeForPreview {
        ManageTilesView(
            snackbarHostState = remember { SnackbarHostState() },
            tileSlots = previewTileSlots,
            selectedTile = previewTileSlots[1],
            onTileSelected = {},
            servers = emptyList(),
            selectedServerId = 0,
            showServerSelector = false,
            onServerSelected = {},
            tileLabel = "Living room",
            onTileLabelChange = {},
            showSubtitle = true,
            tileSubtitle = "Lights",
            onTileSubtitleChange = {},
            entities = emptyList(),
            selectedEntityId = "light.living_room",
            onEntitySelectedId = {},
            onEntityCleared = {},
            entityRegistry = emptyList(),
            deviceRegistry = emptyList(),
            areaRegistry = emptyList(),
            selectedIcon = null,
            onShowIconDialog = {},
            showResetIcon = true,
            onResetIcon = {},
            shouldVibrate = true,
            onShouldVibrateChange = {},
            authRequired = false,
            onAuthRequiredChange = {},
            submitButtonLabel = R.string.tile_save,
            submitEnabled = true,
            onSubmit = {},
        )
    }
}

private val previewTileSlots = listOf(
    TileSlot(id = "tile_1", name = "Tile 1"),
    TileSlot(id = "tile_2", name = "Tile 2"),
)
