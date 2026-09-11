package io.homeassistant.companion.android.settings.sensor.views

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASearchField
import io.homeassistant.companion.android.common.compose.composable.SearchFieldState
import io.homeassistant.companion.android.common.compose.composable.consumeSheetScrollFling
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.composable.rememberSearchFieldState
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel
import io.homeassistant.companion.android.util.compose.safeScreenHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet for multi-select allow list sensor settings (apps, bluetooth, zones, beacons).
 *
 * Renders a search field (when [SensorDetailViewModel.Companion.SettingDialogState.showSearch] is
 * set), a scrollable list of selectable rows, and a fixed footer with cancel and save actions.
 * While the selection state is loading, a centered progress indicator is shown instead of the list.
 *
 * Filtering is performed off the UI thread on [Dispatchers.Default] to keep the sheet responsive on
 * long lists.
 *
 * @param title Heading displayed at the top of the sheet.
 * @param state Current dialog state holding the entries, selection and loading flag.
 * @param onDismiss Invoked when the sheet is dismissed without saving.
 * @param onSave Invoked with the updated state when the user confirms the selection.
 * @param modifier Optional [Modifier] applied to the sheet container.
 * @param bottomSheetState State of the sheet, exposed so tests can provide an already expanded state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SensorDetailSettingSheet(
    title: String,
    state: SensorDetailViewModel.Companion.SettingDialogState,
    onDismiss: () -> Unit,
    onSave: (SensorDetailViewModel.Companion.SettingDialogState) -> Unit,
    modifier: Modifier = Modifier,
    bottomSheetState: SheetState = rememberHAModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val checkedValue = remember(state.entriesSelected) { state.entriesSelected.toMutableStateList() }
    val searchState = rememberSearchFieldState()
    val query = searchState.query
    val matchingEntries by produceState(state.entries, state.entries, query) {
        value = withContext(Dispatchers.Default) {
            filterSettingEntries(state.entries, query)
        }
    }
    // A blank query filters nothing, so the entries are read as they are. Going through
    // [produceState] would leave the list one frame behind [state], which is long enough to render
    // the "no results" placeholder while the entries have in fact just arrived.
    val filteredEntries = if (query.isBlank()) state.entries else matchingEntries

    val screenHeight = safeScreenHeight() - HADimens.SPACE16
    val coroutineScope = rememberCoroutineScope()

    HAModalBottomSheet(
        bottomSheetState = bottomSheetState,
        modifier = modifier,
        onDismissRequest = onDismiss,
    ) {
        SensorDetailSettingSheetContent(
            title = title,
            isLoading = state.isLoading,
            entries = filteredEntries,
            showSearch = state.showSearch,
            searchState = searchState,
            isSelected = { it in checkedValue },
            onToggle = { id, isChecked ->
                if (isChecked) {
                    if (id !in checkedValue) checkedValue.add(id)
                } else {
                    checkedValue.remove(id)
                }
            },
            onCancel = {
                coroutineScope.launch {
                    bottomSheetState.hide()
                    onDismiss()
                }
            },
            onSave = {
                coroutineScope.launch {
                    bottomSheetState.hide()
                    onSave(state.copy(entriesSelected = checkedValue.toList()))
                }
            },
            modifier = Modifier
                .heightIn(max = screenHeight)
                .padding(horizontal = HADimens.SPACE4)
                .consumeSheetScrollFling(),
        )
    }
}

@Composable
private fun SensorDetailSettingSheetContent(
    title: String,
    isLoading: Boolean,
    entries: List<SettingEntry>,
    showSearch: Boolean,
    searchState: SearchFieldState,
    isSelected: (id: String) -> Boolean,
    onToggle: (id: String, isChecked: Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(HADimens.SPACE3),
    ) {
        SheetHeader(
            title = title,
            showSearch = showSearch,
            searchState = searchState,
        )
        SheetEntryList(
            isLoading = isLoading,
            entries = entries,
            isSelected = isSelected,
            onToggle = onToggle,
            modifier = Modifier.weight(1f, fill = false),
        )
        SheetFooter(
            saveEnabled = !isLoading,
            onCancel = onCancel,
            onSave = onSave,
        )
    }
}

@Composable
private fun ColumnScope.SheetHeader(title: String, showSearch: Boolean, searchState: SearchFieldState) {
    Text(
        text = title,
        style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
    )
    if (showSearch) {
        HASearchField(
            state = searchState,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = LocalHAColorScheme.current.colorOnNeutralNormal,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SheetEntryList(
    isLoading: Boolean,
    entries: List<SettingEntry>,
    isSelected: (id: String) -> Boolean,
    onToggle: (id: String, isChecked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The list only takes the height it needs, so the placeholders reserve a minimum area to be
    // centered in instead of hugging the header.
    val placeholderModifier = modifier.fillMaxWidth().heightIn(min = HADimens.SPACE20 * 2)
    when {
        isLoading -> {
            Box(
                modifier = placeholderModifier,
                contentAlignment = Alignment.Center,
            ) {
                HALoading()
            }
        }

        entries.isEmpty() -> {
            Box(
                modifier = placeholderModifier,
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(commonR.string.sensor_setting_allow_list_no_results),
                    style = HATextStyle.Body.copy(
                        color = LocalHAColorScheme.current.colorOnNeutralQuiet,
                    ),
                )
            }
        }

        else -> {
            LazyColumn(modifier = modifier.fillMaxWidth()) {
                items(entries, key = { it.id }) { entry ->
                    SettingRow(
                        entry = entry,
                        checked = isSelected(entry.id),
                        onCheckedChange = { isChecked -> onToggle(entry.id, isChecked) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetFooter(
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = HADimens.SPACE4),
        horizontalArrangement = Arrangement.End,
    ) {
        HAPlainButton(
            text = stringResource(commonR.string.cancel),
            onClick = onCancel,
        )
        Spacer(modifier = Modifier.width(HADimens.SPACE2))
        HAFilledButton(
            text = stringResource(commonR.string.save),
            enabled = saveEnabled,
            onClick = onSave,
        )
    }
}

/**
 * Filters setting entries by matching the query against entry labels (case-insensitive).
 * Returns all entries when the query is blank.
 */
private fun filterSettingEntries(entries: List<SettingEntry>, query: String): List<SettingEntry> {
    val trimmed = query.trim()
    return if (trimmed.isBlank()) {
        entries
    } else {
        entries.filter { it.label.contains(trimmed, ignoreCase = true) }
    }
}

/** Tags the checkbox of the entry [id], so a test can tap it rather than the row around it. */
@VisibleForTesting
internal fun settingEntryCheckboxTag(id: String) = "setting_entry_checkbox_$id"

@Composable
private fun SettingRow(
    entry: SettingEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = LocalHAColorScheme.current
    // Shared with the checkbox so presses anywhere on the row drive its press animation.
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The checkbox centers its 20dp glyph in a 48dp touch target, so it carries a 14dp
            // inset on each side. Pull the row towards the start by that inset to align the glyph
            // with the search field above the list.
            .offset(x = (-14).dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
                indication = null,
                interactionSource = interactionSource,
            )
            .heightIn(min = HADimens.SPACE16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HACheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            interactionSource = interactionSource,
            modifier = Modifier.testTag(settingEntryCheckboxTag(entry.id)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.primary,
                style = HATextStyle.Body.copy(
                    textAlign = TextAlign.Start,
                    color = colorScheme.colorTextPrimary,
                ),
            )
            if (entry.secondary != null) {
                Spacer(Modifier.height(HADimens.SPACE1))
                Text(
                    text = entry.secondary,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SensorDetailSettingsSheetPreview() {
    val entries = listOf(
        SettingEntry("com.google.android.apps.maps", "Maps\n(com.google.android.apps.maps)"),
        SettingEntry("com.spotify.music", "Spotify\n(com.spotify.music)"),
        SettingEntry("com.netflix.mediaclient", "Netflix\n(com.netflix.mediaclient)"),
        SettingEntry("com.whatsapp", "WhatsApp\n(com.whatsapp)"),
    )
    PreviewSheet(
        entries = entries,
        entriesSelected = listOf("com.spotify.music", "com.netflix.mediaclient"),
    )
}

@Preview
@Composable
private fun SensorDetailSettingsSheetLoadingPreview() {
    PreviewSheet(entries = emptyList(), isLoading = true)
}

@Preview
@Composable
private fun SensorDetailSettingsSheetNoResultPreview() {
    PreviewSheet(entries = emptyList())
}

/**
 * Renders the sheet with a [SheetState] already settled at [SheetValue.Expanded], since the default
 * state starts hidden and a static frame would show nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSheet(
    entries: List<SettingEntry>,
    entriesSelected: List<String> = emptyList(),
    isLoading: Boolean = false,
) {
    HAThemeForPreview(modifier = Modifier.fillMaxSize()) {
        SensorDetailSettingSheet(
            title = "Monitored apps",
            state = SensorDetailViewModel.Companion.SettingDialogState(
                setting = SensorSetting(
                    sensorId = "last_notification",
                    name = "allow_list",
                    value = entriesSelected.joinToString(),
                    valueType = SensorSettingType.LIST_APPS,
                ),
                isLoading = isLoading,
                entries = entries,
                entriesSelected = entriesSelected,
            ),
            onDismiss = {},
            onSave = {},
            bottomSheetState = SheetState(
                skipPartiallyExpanded = true,
                // Thresholds only affect drag gestures, which never happen in previews.
                positionalThreshold = { 0f },
                velocityThreshold = { 0f },
                initialValue = SheetValue.Expanded,
            ),
        )
    }
}
