package io.homeassistant.companion.android.settings.shortcuts.v2.views.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.IconicsPainter
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HARadius
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutError
import io.homeassistant.companion.android.common.data.shortcuts.impl.entities.ShortcutType
import io.homeassistant.companion.android.settings.shortcuts.v2.AppShortcutItem
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListAction
import io.homeassistant.companion.android.settings.shortcuts.v2.ShortcutsListState
import io.homeassistant.companion.android.settings.shortcuts.v2.views.components.EmptyStateContent
import io.homeassistant.companion.android.settings.shortcuts.v2.views.components.EmptyStateNoServers
import io.homeassistant.companion.android.settings.shortcuts.v2.views.components.ErrorStateContent
import io.homeassistant.companion.android.settings.shortcuts.v2.views.components.NotSupportedStateContent
import io.homeassistant.companion.android.settings.shortcuts.v2.views.preview.ShortcutPreviewData
import io.homeassistant.companion.android.util.compose.MdcAlertDialog
import io.homeassistant.companion.android.util.compose.screenWidth
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.plus
import io.homeassistant.companion.android.util.safeBottomPaddingValues
import io.homeassistant.companion.android.util.safeBottomWindowInsets

private val COMPACT_WIDTH_BREAKPOINT = 600.dp

/**
 * Pure UI composable for the Manage Shortcuts screen.
 * This composable has no ViewModel dependencies and can be previewed.
 */
@Composable
internal fun ShortcutsListScreen(
    state: ShortcutsListState,
    dispatch: (ShortcutsListAction) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    val dismissCreateDialog: () -> Unit = { showCreateDialog = false }
    Scaffold(
        floatingActionButton = {
            if (!state.isLoading && state.error != ShortcutError.ApiNotSupported) {
                FloatingActionButton(
                    modifier = Modifier.padding(safeBottomPaddingValues(applyHorizontal = false)),
                    containerColor = LocalHAColorScheme.current.colorFillPrimaryLoudResting,
                    contentColor = LocalHAColorScheme.current.colorOnPrimaryLoud,
                    onClick = { showCreateDialog = true },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_shortcut),
                    )
                }
            }
        },
        contentWindowInsets = safeBottomWindowInsets(),
        modifier = modifier,
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            when {
                state.isLoading -> LoadingState()
                state.error == ShortcutError.ApiNotSupported -> NotSupportedStateContent()
                state.error == ShortcutError.NoServers -> EmptyStateNoServers()
                state.hasError -> {
                    ErrorStateContent(onRetry = onRetry)
                }
                state.isEmpty -> {
                    EmptyStateContent()
                }
                else -> ShortcutsList(state = state, dispatch = dispatch)
            }
        }
    }

    if (showCreateDialog) {
        CreateShortcutDialog(
            state = state,
            onCreateAppShortcut = {
                if (state.maxAppShortcuts?.let { state.appShortcutItems.size < it } == true) {
                    dismissCreateDialog()
                    dispatch(ShortcutsListAction.CreateAppShortcut)
                }
            },
            onCreateHomeShortcut = {
                if (state.isHomeSupported) {
                    dismissCreateDialog()
                    dispatch(ShortcutsListAction.CreateHomeShortcut)
                }
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
private fun ShortcutsList(state: ShortcutsListState, dispatch: (ShortcutsListAction) -> Unit) {
    val homeItems = state.homeShortcutItems
    val isCompactScreen = screenWidth() < COMPACT_WIDTH_BREAKPOINT
    val columnsCount = if (isCompactScreen) 3 else 4

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnsCount),
        contentPadding = PaddingValues(all = HADimens.SPACE4) +
            safeBottomPaddingValues(applyHorizontal = false) +
            PaddingValues(bottom = HADimens.SPACE18),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        if (state.appShortcutItems.isNotEmpty() && state.maxAppShortcuts != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    text = stringResource(R.string.shortcut_v2_app_shortcuts_header),
                    subtitle = stringResource(R.string.shortcut_v2_dynamic_section_subtitle),
                    showAppIcon = true,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppShortcutsLongPressPreview(
                    items = state.appShortcutItems,
                    maxAppShortcuts = state.maxAppShortcuts,
                    onEditAppShortcut = { index -> dispatch(ShortcutsListAction.EditAppShortcut(index)) },
                )
            }
        }

        if (homeItems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    text = stringResource(R.string.shortcut_v2_home_screen_shortcuts_header),
                    subtitle = stringResource(R.string.shortcut_v2_pinned_section_subtitle),
                )
            }
            items(items = homeItems) { summary ->
                val label = summary.label.ifBlank { summary.id }
                ShortcutGridItem(
                    label = label,
                    iconName = summary.selectedIconName,
                    onClick = { dispatch(ShortcutsListAction.EditHomeShortcut(summary.id)) },
                )
            }
        }
    }
}

@Composable
private fun AppShortcutsLongPressPreview(
    items: List<AppShortcutItem>,
    maxAppShortcuts: Int,
    onEditAppShortcut: (Int) -> Unit,
) {
    val colors = LocalHAColorScheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HADimens.SPACE1),
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        Text(
            text = pluralStringResource(R.plurals.shortcut_v2_dynamic_slots_capacity, maxAppShortcuts, maxAppShortcuts),
            style = HATextStyle.BodyMedium,
            color = colors.colorTextSecondary,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.width(HADimens.SPACE6)) {
                items.forEachIndexed { position, item ->
                    val slotNumber = item.index + 1
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = HADimens.SPACE12),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = slotNumber.toString(),
                            style = HATextStyle.BodyMedium,
                            textAlign = TextAlign.Center,
                            color = colors.colorTextPrimary,
                            maxLines = 1,
                        )
                    }
                    if (position != items.lastIndex) {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(HARadius.M))
                    .background(
                        color = colors.colorFillPrimaryQuietResting,
                    ),
            ) {
                items.forEachIndexed { position, item ->
                    val label = item.summary.label.ifBlank {
                        stringResource(R.string.shortcut_n, item.index + 1)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = HADimens.SPACE12)
                            .clickable(onClick = { onEditAppShortcut(item.index) })
                            .padding(horizontal = HADimens.SPACE3, vertical = HADimens.SPACE2),
                        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShortcutListIcon(
                            iconName = item.summary.selectedIconName,
                            modifier = Modifier.size(HADimens.SPACE5),
                            tint = colors.colorFillPrimaryLoudResting,
                        )
                        Text(
                            text = label,
                            style = HATextStyle.BodyMedium,
                            color = colors.colorTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (position != items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.colorBorderNeutralQuiet),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, subtitle: String? = null, showAppIcon: Boolean = false) {
    val colors = LocalHAColorScheme.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE2),
        ) {
            if (showAppIcon) {
                Box(
                    modifier = Modifier
                        .size(HADimens.SPACE14)
                        .background(
                            color = colors.colorFillPrimaryLoudResting,
                            shape = RoundedCornerShape(HARadius.XL),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stat_ic_notification_blue),
                        contentDescription = null,
                        tint = colors.colorOnPrimaryLoud,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(HADimens.SPACE1)) {
                Text(
                    text = text,
                    style = HATextStyle.HeadlineMedium,
                    color = colors.colorFillPrimaryLoudResting,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = HATextStyle.BodyMedium,
                        color = colors.colorTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutGridItem(label: String, iconName: String?, onClick: () -> Unit) {
    val colors = LocalHAColorScheme.current
    val isCompactScreen = screenWidth() < COMPACT_WIDTH_BREAKPOINT
    val badgeSize = if (isCompactScreen) HADimens.SPACE14 else HADimens.SPACE16
    val iconSize = if (isCompactScreen) HADimens.SPACE7 else HADimens.SPACE8
    val labelGap = HADimens.SPACE2
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = HADimens.SPACE2, vertical = HADimens.SPACE2),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(badgeSize)
                .background(
                    color = colors.colorFillPrimaryQuietResting,
                    shape = RoundedCornerShape(HARadius.XL),
                ),
            contentAlignment = Alignment.Center,
        ) {
            ShortcutListIcon(
                iconName = iconName,
                modifier = Modifier.size(iconSize),
                tint = colors.colorFillPrimaryLoudResting,
            )
        }
        Spacer(modifier = Modifier.size(labelGap))
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

@Composable
private fun ShortcutListIcon(iconName: String?, tint: Color, modifier: Modifier = Modifier) {
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
        tint = tint,
    )
}

@Composable
private fun CreateShortcutDialog(
    state: ShortcutsListState,
    onCreateAppShortcut: () -> Unit,
    onCreateHomeShortcut: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val canCreateAppShortcut = state.maxAppShortcuts?.let { state.appShortcutItems.size < it } == true
    val canCreateHomeShortcut = state.isHomeSupported
    MdcAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.shortcut_v2_create_dialog_subtitle)) },
        content = {
            LazyColumn {
                item {
                    ShortcutTypeOptionRow(
                        icon = CommunityMaterial.Icon2.cmd_flash,
                        label = stringResource(R.string.shortcut_v2_add_to_app_shortcuts),
                        description = if (canCreateAppShortcut) {
                            stringResource(R.string.shortcut_v2_add_to_app_shortcuts_subtitle)
                        } else {
                            stringResource(R.string.shortcut_dynamic_slots_full)
                        },
                        enabled = canCreateAppShortcut,
                        onClick = onCreateAppShortcut,
                    )
                }
                item {
                    ShortcutTypeOptionRow(
                        icon = CommunityMaterial.Icon3.cmd_view_dashboard,
                        label = stringResource(R.string.shortcut_v2_add_to_home_screen),
                        description = if (canCreateHomeShortcut) {
                            stringResource(R.string.shortcut_v2_add_to_home_screen_subtitle)
                        } else {
                            stringResource(R.string.shortcut_pin_not_supported)
                        },
                        enabled = canCreateHomeShortcut,
                        onClick = onCreateHomeShortcut,
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
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalHAColorScheme.current
    val primaryTextColor = if (enabled) colors.colorTextPrimary else colors.colorTextDisabled
    val secondaryTextColor = if (enabled) colors.colorTextSecondary else colors.colorTextDisabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HADimens.SPACE4, vertical = HADimens.SPACE3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        Image(
            asset = icon,
            colorFilter = ColorFilter.tint(primaryTextColor),
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
                color = primaryTextColor,
            )
            Text(
                text = description,
                style = HATextStyle.BodyMedium,
                color = secondaryTextColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "Shortcuts List")
@Composable
private fun ShortcutsListScreenPreview() {
    val appSummaries = ShortcutPreviewData.buildAppSummaries(
        count = 4,
        type = ShortcutType.LOVELACE,
    ).toList()
    val baseHome = ShortcutPreviewData.buildHomeSummaries().first()
    val homeSummaries = listOf(
        baseHome,
        baseHome.copy(
            id = "pinned_2",
            label = "Home 2",
        ),
        baseHome.copy(
            id = "pinned_3",
            label = "Home 3",
        ),
    ).toList()
    HAThemeForPreview {
        ShortcutsListScreen(
            state = ShortcutPreviewData.buildListState(
                appSummaries = appSummaries,
                homeSummaries = homeSummaries,
            ),
            dispatch = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Shortcuts List Loading")
@Composable
private fun ShortcutsListScreenLoadingPreview() {
    HAThemeForPreview {
        ShortcutsListScreen(
            state = ShortcutPreviewData.buildListState(isLoading = true),
            dispatch = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Shortcuts List Empty")
@Composable
private fun ShortcutsListScreenEmptyPreview() {
    HAThemeForPreview {
        ShortcutsListScreen(
            state = ShortcutPreviewData.buildListState(
                appSummaries = emptyList(),
                homeSummaries = emptyList(),
            ),
            dispatch = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Shortcuts List Error")
@Composable
private fun ShortcutsListScreenErrorPreview() {
    HAThemeForPreview {
        ShortcutsListScreen(
            state = ShortcutPreviewData.buildListState(error = ShortcutError.Unknown),
            dispatch = {},
            onRetry = {},
        )
    }
}

@Preview(name = "Shortcuts List Not Supported")
@Composable
private fun ShortcutsListScreenNotSupportedPreview() {
    HAThemeForPreview {
        ShortcutsListScreen(
            state = ShortcutsListState().copy(
                isLoading = false,
                error = ShortcutError.ApiNotSupported,
            ),
            dispatch = {},
            onRetry = {},
        )
    }
}
