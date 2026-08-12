package io.homeassistant.companion.android.settings.qs.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAHorizontalDivider
import io.homeassistant.companion.android.common.compose.composable.HAIconButton
import io.homeassistant.companion.android.common.compose.composable.HASwitch
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.settings.qs.ManageTilesState
import io.homeassistant.companion.android.settings.qs.ManageTilesViewModel
import io.homeassistant.companion.android.settings.qs.TileId
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.icondialog.IconDialog
import io.homeassistant.companion.android.util.safeBottomWindowInsets

@Composable
internal fun ManageTilesScreen(viewModel: ManageTilesViewModel, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showIconDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.tileInfoSnackbar.collect { resId ->
            snackbarHostState.showSnackbar(resources.getString(resId))
        }
    }

    if (showIconDialog) {
        // TODO Migrate IconDialog to Material 3 https://github.com/home-assistant/android/issues/7156
        HomeAssistantAppTheme {
            IconDialog(
                onSelect = { icon ->
                    viewModel.selectIcon(icon)
                    showIconDialog = false
                },
                onDismissRequest = { showIconDialog = false },
            )
        }
    }

    ManageTilesContent(
        snackbarHostState = snackbarHostState,
        state = state,
        submitEnabled = state.submitEnabled,
        onTileSelected = viewModel::selectTile,
        onServerSelected = viewModel::selectServerId,
        onTileLabelChange = viewModel::setTileLabel,
        onTileSubtitleChange = viewModel::setTileSubtitle,
        onSelectionChanged = viewModel::selectEntityId,
        onShowIconDialog = { showIconDialog = true },
        onResetIcon = { viewModel.selectIcon(null) },
        onShouldVibrateChange = viewModel::setShouldVibrate,
        onAuthRequiredChange = viewModel::setAuthRequired,
        onSubmit = { viewModel.addTile(context) },
        modifier = modifier,
    )
}

@Composable
internal fun ManageTilesContent(
    snackbarHostState: SnackbarHostState,
    state: ManageTilesState,
    submitEnabled: Boolean,
    onTileSelected: (id: TileId) -> Unit,
    onServerSelected: (Int) -> Unit,
    onTileLabelChange: (String) -> Unit,
    onTileSubtitleChange: (String) -> Unit,
    onSelectionChanged: (String?) -> Unit,
    onShowIconDialog: () -> Unit,
    onResetIcon: () -> Unit,
    onShouldVibrateChange: (Boolean) -> Unit,
    onAuthRequiredChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
            )
        },
        contentWindowInsets = safeBottomWindowInsets(applyHorizontal = true),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(all = HADimens.SPACE4),
            horizontalAlignment = Alignment.Start,
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
                    label = stringResource(commonR.string.tile_server),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TileConfigContent(
                state = state,
                onSelectionChanged = onSelectionChanged,
                onAuthRequiredChange = onAuthRequiredChange,
                onShowIconDialog = onShowIconDialog,
                onResetIcon = onResetIcon,
                onShouldVibrateChange = onShouldVibrateChange,
            )

            HAFilledButton(
                text = stringResource(state.submitButtonLabel),
                onClick = onSubmit,
                enabled = submitEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ColumnScope.TileLabelContent(
    state: ManageTilesState,
    onTileSelected: (id: TileId) -> Unit,
    onTileLabelChange: (String) -> Unit,
    onTileSubtitleChange: (String) -> Unit,
) {
    val res = LocalResources.current
    val tiles = remember(state.tileSlotItems) {
        state.tileSlotItems.map { slot ->
            HADropdownItem(
                key = slot.id,
                label = res.getString(
                    commonR.string.tile_slot_label,
                    res.getString(slot.nameRes),
                    slot.label ?: res.getString(commonR.string.not_set),
                ),
            )
        }
    }

    HADropdownMenu(
        items = tiles,
        selectedKey = state.selectedTileId,
        onItemSelected = onTileSelected,
        label = stringResource(commonR.string.tile_select),
        modifier = Modifier.fillMaxWidth(),
    )

    HAHorizontalDivider()

    Text(
        text = stringResource(commonR.string.tile_required_field_hint),
        style = HATextStyle.BodyMedium,
        color = LocalHAColorScheme.current.colorTextSecondary,
    )

    HATextField(
        value = state.tileLabel,
        onValueChange = onTileLabelChange,
        label = { Text(text = stringResource(commonR.string.tile_label)) },
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.showSubtitle) {
        HATextField(
            value = state.tileSubtitle,
            onValueChange = onTileSubtitleChange,
            label = { Text(text = stringResource(commonR.string.tile_subtitle)) },
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.TileConfigContent(
    state: ManageTilesState,
    onSelectionChanged: (String?) -> Unit,
    onShowIconDialog: () -> Unit,
    onResetIcon: () -> Unit,
    onShouldVibrateChange: (Boolean) -> Unit,
    onAuthRequiredChange: (Boolean) -> Unit,
) {
    EntityPicker(
        displayState = state.entityDisplayState,
        selectedEntityId = state.selectedEntityId,
        onSelectionChanged = onSelectionChanged,
        addButtonText = stringResource(commonR.string.tile_entity),
    )

    TileIconRow(
        selectedIcon = state.selectedIcon,
        showResetIcon = state.showResetIcon,
        onShowIconDialog = onShowIconDialog,
        onResetIcon = onResetIcon,
    )

    LabeledSwitchRow(
        label = stringResource(commonR.string.tile_vibrate),
        checked = state.selectedShouldVibrate,
        onCheckedChange = onShouldVibrateChange,
    )

    LabeledSwitchRow(
        label = stringResource(commonR.string.tile_auth_required),
        checked = state.tileAuthRequired,
        onCheckedChange = onAuthRequiredChange,
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
    val iconContentDescription = stringResource(commonR.string.tile_icon)
    val colorScheme = LocalHAColorScheme.current
    val buttonShape = RoundedCornerShape(HARadius.Pill)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(commonR.string.tile_icon),
            style = HATextStyle.Body,
            modifier = Modifier.padding(end = HADimens.SPACE2),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showResetIcon) {
            HAIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                onClick = onResetIcon,
                contentDescription = stringResource(commonR.string.undo),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .border(HABorderWidth.S, colorScheme.colorBorderNeutralQuiet, buttonShape)
                .clip(buttonShape)
                .clickable(onClick = onShowIconDialog, role = Role.Button)
                .padding(horizontal = HADimens.SPACE4)
                .heightIn(min = 40.dp)
                .semantics { contentDescription = iconContentDescription },
        ) {
            if (selectedIcon != null) {
                Image(
                    selectedIcon,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorScheme.colorOnPrimaryNormal),
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(
                    text = stringResource(commonR.string.select),
                    style = HATextStyle.Button,
                    color = colorScheme.colorOnPrimaryNormal,
                )
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
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
        ManageTilesContent(
            snackbarHostState = remember { SnackbarHostState() },
            state = previewState,
            submitEnabled = false,
            onTileSelected = {},
            onServerSelected = {},
            onTileLabelChange = {},
            onTileSubtitleChange = {},
            onSelectionChanged = {},
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
        ManageTilesContent(
            snackbarHostState = remember { SnackbarHostState() },
            submitEnabled = false,
            state = previewState.copy(
                selectedTileId = TileId("tile_2"),
                tileLabel = "Living room",
                tileSubtitle = "Lights",
                selectedEntityId = "light.living_room",
                submitButtonLabel = commonR.string.tile_save,
            ),
            onTileSelected = {},
            onServerSelected = {},
            onTileLabelChange = {},
            onTileSubtitleChange = {},
            onSelectionChanged = {},
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
    selectedTileId = TileId("tile_1"),
    selectedServerId = 0,
    tileLabel = "",
    tileSubtitle = "",
    selectedEntityId = "",
    submitButtonLabel = commonR.string.tile_add,
    showSubtitle = true,
)
