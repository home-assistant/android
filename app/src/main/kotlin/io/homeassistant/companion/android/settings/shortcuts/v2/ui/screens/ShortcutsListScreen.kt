package io.homeassistant.companion.android.settings.shortcuts.v2.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.mikepenz.iconics.compose.IconicsPainter
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HABorderWidth
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutSummary
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType
import io.homeassistant.companion.android.settings.shortcuts.v2.DynamicShortcutItem
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListAction
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListState
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.components.EmptyStateContent
import io.homeassistant.companion.android.settings.shortcuts.v2.ui.preview.ShortcutPreviewData
import io.homeassistant.companion.android.util.compose.MdcAlertDialog
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Pure UI composable for the Manage Shortcuts screen.
 * This composable has no ViewModel dependencies and can be previewed.
 */
@RequiresApi(Build.VERSION_CODES.N_MR1) // TODO: Check why do we need N_MR1 here
@Composable
internal fun ShortcutsListScreen(
    state: ShortcutsListState,
    dispatch: (ShortcutsListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val dismissCreateDialog: () -> Unit = { showCreateDialog = false }
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.padding(safeBottomPaddingValues(applyHorizontal = false)),
                containerColor = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
                contentColor = LocalHAColorScheme.current.colorOnPrimaryLoud,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_shortcut)) },
                onClick = { showCreateDialog = true },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0), // TODO: Check how to avoid this
        modifier = modifier,
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            when {
                state.isLoading -> LoadingState()
                state.isEmpty -> {
                    EmptyStateContent(hasServers = true)
                }
                else -> ShortcutsList(
                    dynamicItems = state.dynamicItems,
                    pinnedItems = state.pinnedShortcuts,
                    onEditDynamic = { dispatch(ShortcutsListAction.EditDynamic(it)) },
                    onEditPinned = { dispatch(ShortcutsListAction.EditPinned(it)) },
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateShortcutDialog(
            onCreateDynamic = {
                dismissCreateDialog()
                dispatch(ShortcutsListAction.CreateDynamic)
            },
            onCreatePinned = {
                dismissCreateDialog()
                dispatch(ShortcutsListAction.CreatePinned)
            },
            onDismissRequest = dismissCreateDialog,
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        HALoading()
    }
}

@Composable
private fun ShortcutsList(
    dynamicItems: ImmutableList<DynamicShortcutItem>,
    pinnedItems: ImmutableList<ShortcutSummary>,
    onEditDynamic: (Int) -> Unit,
    onEditPinned: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(all = HADimens.SPACE4) +
            safeBottomPaddingValues(applyHorizontal = false) +
            PaddingValues(bottom = HADimens.SPACE18),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        if (dynamicItems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(text = stringResource(R.string.shortcut_dynamic_header))
            }
            items(items = dynamicItems) { item ->
                val label = item.summary.label.ifBlank {
                    stringResource(R.string.shortcut_n, item.index + 1)
                }
                ShortcutGridItem(
                    label = label,
                    iconName = item.summary.selectedIconName,
                    onClick = { onEditDynamic(item.index) },
                )
            }
        }

        if (pinnedItems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(text = stringResource(R.string.shortcut_pinned))
            }
            items(items = pinnedItems) { summary ->
                val label = summary.label.ifBlank { summary.id }
                ShortcutGridItem(
                    label = label,
                    iconName = summary.selectedIconName,
                    onClick = { onEditPinned(summary.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = HATextStyle.HeadlineMedium,
        color = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ShortcutGridItem(label: String, iconName: String?, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(HABorderWidth.S, colors.colorBorderNeutralQuiet),
        color = colors.colorSurfaceLow,
        contentColor = colors.colorTextPrimary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HADimens.SPACE3, vertical = HADimens.SPACE3),
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShortcutListIcon(
                iconName = iconName,
                modifier = Modifier.size(HADimens.SPACE7),
            )
            Text(
                text = label,
                style = HATextStyle.BodyMedium,
                color = colors.colorTextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ShortcutListIcon(iconName: String?, modifier: Modifier = Modifier) {
    val icon = remember(iconName) { iconName?.let(CommunityMaterial::getIconByMdiName) }
    val painter = if (icon != null) {
        remember(icon) { IconicsPainter(icon) }
    } else {
        painterResource(R.drawable.ic_stat_ic_notification_blue)
    }
    Icon(
        painter = painter,
        contentDescription = null, // TODO: Add content description
        modifier = modifier,
    )
}

@Composable
private fun CreateShortcutDialog(
    onCreateDynamic: () -> Unit,
    onCreatePinned: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    MdcAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.add_shortcut)) },
        content = {
            LazyColumn {
                item {
                    ShortcutTypeOptionRow(
                        icon = CommunityMaterial.Icon2.cmd_flash,
                        label = stringResource(R.string.shortcut_type_dynamic),
                        subtitle = null,
                        enabled = true,
                        onClick = onCreateDynamic,
                    )
                }
                item {
                    ShortcutTypeOptionRow(
                        icon = CommunityMaterial.Icon3.cmd_view_dashboard,
                        label = stringResource(R.string.shortcut_type_pinned),
                        subtitle = null,
                        enabled = true,
                        onClick = onCreatePinned,
                    )
                }
            }
        },
        onCancel = onDismissRequest,
    )
}

@Composable
private fun ShortcutTypeOptionRow(
    icon: IIcon,
    label: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        Image(
            asset = icon,
            colorFilter = ColorFilter.tint(LocalHAColorScheme.current.colorTextPrimary),
            contentDescription = label,
            modifier = Modifier.size(HADimens.SPACE5),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = label,
                style = HATextStyle.BodyMedium,
                color = LocalHAColorScheme.current.colorTextPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = HATextStyle.BodyMedium,
                    color = LocalHAColorScheme.current.colorTextSecondary,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Preview(name = "Manage Shortcuts")
@Composable
private fun ShortcutsListScreenPreview() {
    val dynamicSummaries = ShortcutPreviewData.buildDynamicSummaries(
        count = 4,
        type = ShortcutType.LOVELACE,
    ).toImmutableList()
    val basePinned = ShortcutPreviewData.buildPinnedSummaries().first()
    val pinnedSummaries = listOf(
        basePinned,
        basePinned.copy(
            id = "pinned_2",
            label = "Pinned 2",
        ),
        basePinned.copy(
            id = "pinned_3",
            label = "Pinned 3",
        ),
    ).toImmutableList()
    HAThemeForPreview {
        ShortcutsListScreen(
            state = ShortcutPreviewData.buildListState(
                dynamicSummaries = dynamicSummaries,
                pinnedSummaries = pinnedSummaries,
            ),
            dispatch = {},
        )
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Preview(name = "Manage Shortcuts Loading")
@Composable
private fun ShortcutsListScreenLoadingPreview() {
    HAThemeForPreview {
        ShortcutsListScreen(
            state = ShortcutPreviewData.buildListState(isLoading = true),
            dispatch = {},
        )
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
@Preview(name = "Manage Shortcuts Empty")
@Composable
private fun ShortcutsListScreenEmptyPreview() {
    HAThemeForPreview {
        ShortcutsListScreen(
            state = ShortcutPreviewData.buildListState(
                dynamicSummaries = persistentListOf(),
                pinnedSummaries = persistentListOf(),
            ),
            dispatch = {},
        )
    }
}
