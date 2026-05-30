package io.homeassistant.companion.android.settings.sensor.views

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HALoading
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASearchField
import io.homeassistant.companion.android.common.compose.composable.consumeSheetScrollFling
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel
import io.homeassistant.companion.android.util.compose.safeScreenHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Threshold above which the search field becomes visible to help users navigate long lists. */
private const val SEARCH_VISIBILITY_THRESHOLD = 10

/**
 * Bottom sheet for multi-select allow list sensor settings (apps, bluetooth, zones, beacons).
 *
 * Renders a search field (when the entry list exceeds [SEARCH_VISIBILITY_THRESHOLD]), a scrollable
 * list of selectable rows, and a fixed footer with cancel and save actions. While the selection
 * state is loading, a centered progress indicator is shown instead of the list.
 *
 * Filtering is performed off the UI thread on [Dispatchers.Default] to keep the sheet responsive on
 * long lists. The search field debounces the query so the list does not re-filter on every keystroke.
 *
 * @param title Heading displayed at the top of the sheet.
 * @param state Current dialog state holding the entries, selection and loading flag.
 * @param onDismiss Invoked when the sheet is dismissed without saving.
 * @param onSave Invoked with the updated state when the user confirms the selection.
 * @param modifier Optional [Modifier] applied to the sheet container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SensorDetailSettingSheet(
    title: String,
    state: SensorDetailViewModel.Companion.SettingDialogState,
    onDismiss: () -> Unit,
    onSave: (SensorDetailViewModel.Companion.SettingDialogState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = remember(state.entries) {
        state.entries.map { (id, label) -> SettingEntry(id, label) }
    }
    val checkedValue = remember { state.entriesSelected.toMutableStateList() }
    var searchQuery by remember { mutableStateOf("") }
    // Filtering runs off the UI thread on Dispatchers.Default to keep the sheet responsive on long lists.
    var filteredEntries by remember(entries) { mutableStateOf(entries) }
    LaunchedEffect(entries, searchQuery) {
        filteredEntries = withContext(Dispatchers.Default) {
            filterSettingEntries(entries, searchQuery)
        }
    }
    val showSearch = entries.size > SEARCH_VISIBILITY_THRESHOLD

    val bottomSheetState = rememberHAModalBottomSheetState(skipPartiallyExpanded = true)
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
            showSearch = showSearch,
            searchQuery = searchQuery,
            onQueryChange = { searchQuery = it },
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
                    val joinedValue = joinSelectedValues(checkedValue)
                    onSave(state.copy(setting = state.setting.copy(value = joinedValue)))
                }
            },
            modifier = Modifier
                .height(screenHeight)
                .padding(horizontal = HADimens.SPACE4)
                .consumeSheetScrollFling(),
        )
    }
}

/**
 * Inner content of the sensor setting bottom sheet, extracted for preview and screenshot testing.
 *
 * Renders the sheet header (title + optional search field), the entry list or loading indicator,
 * and the cancel/save footer. The [HAModalBottomSheet] wrapper is intentionally absent so this
 * composable can be rendered in isolation via [androidx.compose.ui.tooling.preview.Preview].
 *
 * @param title Heading displayed at the top of the content area.
 * @param isLoading When true, a loading indicator is shown in place of the entry list.
 * @param entries Filtered list of selectable entries to display.
 * @param showSearch When true, a search field is rendered below the title.
 * @param searchQuery Current text in the search field.
 * @param onQueryChange Invoked when the search query changes.
 * @param isSelected Returns whether the entry with the given ID is currently checked.
 * @param onToggle Invoked when the user checks or unchecks an entry.
 * @param onCancel Invoked when the user taps Cancel.
 * @param onSave Invoked when the user taps Save.
 * @param modifier Optional [Modifier] applied to the root [Column].
 */
@Composable
internal fun SensorDetailSettingSheetContent(
    title: String,
    isLoading: Boolean,
    entries: List<SettingEntry>,
    showSearch: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
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
            searchQuery = searchQuery,
            onQueryChange = onQueryChange,
        )
        SheetEntryList(
            isLoading = isLoading,
            entries = entries,
            isSelected = isSelected,
            onToggle = onToggle,
            modifier = Modifier.weight(1f),
        )
        SheetFooter(
            saveEnabled = !isLoading,
            onCancel = onCancel,
            onSave = onSave,
        )
    }
}

@Composable
private fun ColumnScope.SheetHeader(
    title: String,
    showSearch: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
) {
    Text(
        text = title,
        style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
    )
    if (showSearch) {
        HASearchField(
            query = searchQuery,
            onQueryChange = onQueryChange,
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
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HALoading()
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxWidth()) {
            items(entries, key = { it.id }) { entry ->
                BottomSheetSettingRow(
                    label = entry.label,
                    checked = isSelected(entry.id),
                    onClick = { isChecked -> onToggle(entry.id, isChecked) },
                )
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
        modifier = modifier.fillMaxWidth(),
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

/** Identifier-and-label pair displayed inside the allow-list sheet. */
@VisibleForTesting
internal data class SettingEntry(val id: String, val label: String)

/**
 * Filters setting entries by matching the query against entry labels (case-insensitive).
 * Returns all entries when the query is blank.
 */
@VisibleForTesting
internal fun filterSettingEntries(entries: List<SettingEntry>, query: String): List<SettingEntry> {
    val trimmed = query.trim()
    return if (trimmed.isBlank()) {
        entries
    } else {
        entries.filter { it.label.contains(trimmed, ignoreCase = true) }
    }
}

/**
 * Joins selected entry IDs into the comma-separated string format expected by [SensorDetailViewModel].
 *
 * Mirrors the legacy formatting used elsewhere in the sensor settings code, removing list bracket
 * artifacts that originate from older Java-style toString conversions.
 */
internal fun joinSelectedValues(values: List<String>): String {
    return values.joinToString().replace("[", "").replace("]", "")
}

/**
 * Single selectable row used inside [SensorDetailSettingSheet]. Built with Material 3 components and
 * the project's design tokens; intentionally separate from [SensorDetailSettingRow] used by the
 * legacy Material 2 dialog.
 */
@Composable
private fun BottomSheetSettingRow(
    label: String,
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = parseSettingLabel(label)
    val colorScheme = LocalHAColorScheme.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onClick(!checked) }
            .heightIn(min = HADimens.SPACE16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = colorScheme.colorFillPrimaryLoudResting,
                uncheckedColor = colorScheme.colorBorderNeutralNormal,
                checkmarkColor = colorScheme.colorSurfaceDefault,
                disabledCheckedColor = colorScheme.colorFillDisabledLoudResting,
                disabledUncheckedColor = colorScheme.colorBorderNeutralQuiet,
            ),
            modifier = Modifier.size(width = HADimens.SPACE12, height = HADimens.SPACE12),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = parsed.primary,
                style = HATextStyle.Body.copy(
                    textAlign = TextAlign.Start,
                    color = colorScheme.colorTextPrimary,
                ),
            )
            if (parsed.secondary != null) {
                Spacer(Modifier.height(HADimens.SPACE1))
                Text(
                    text = parsed.secondary,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                )
            }
        }
    }
}
