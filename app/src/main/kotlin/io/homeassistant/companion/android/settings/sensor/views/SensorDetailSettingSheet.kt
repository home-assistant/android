package io.homeassistant.companion.android.settings.sensor.views

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAFilledButton
import io.homeassistant.companion.android.common.compose.composable.HAModalBottomSheet
import io.homeassistant.companion.android.common.compose.composable.HAPlainButton
import io.homeassistant.companion.android.common.compose.composable.HASearchField
import io.homeassistant.companion.android.common.compose.composable.rememberHAModalBottomSheetState
import io.homeassistant.companion.android.common.compose.theme.HADimens
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import io.homeassistant.companion.android.settings.sensor.SensorDetailViewModel
import io.homeassistant.companion.android.util.compose.safeScreenHeight
import kotlinx.coroutines.Dispatchers
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
    val filteredEntries = rememberFilteredSettingEntries(entries, searchQuery)
    val showSearch = entries.size > SEARCH_VISIBILITY_THRESHOLD

    val bottomSheetState = rememberHAModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = safeScreenHeight() - HADimens.SPACE16

    val nestedScrollFlingGuard = remember { nestedScrollFlingGuard() }

    HAModalBottomSheet(
        bottomSheetState = bottomSheetState,
        modifier = modifier,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .height(screenHeight)
                .padding(horizontal = HADimens.SPACE4)
                .nestedScroll(nestedScrollFlingGuard)
                .pointerInput(Unit) {
                    // Consume vertical drag gestures to prevent BottomSheet from interpreting them
                    // as collapse gestures while the user scrolls the entry list.
                    detectVerticalDragGestures { _, _ -> }
                },
        ) {
            SheetHeader(
                title = title,
                showSearch = showSearch,
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
            )
            SheetEntryList(
                loading = state.loading,
                entries = filteredEntries,
                checkedValue = checkedValue,
                modifier = Modifier.weight(1f),
            )
            SheetFooter(
                saveEnabled = !state.loading,
                onCancel = onDismiss,
                onSave = {
                    val joinedValue = joinSelectedValues(checkedValue)
                    onSave(state.copy(setting = state.setting.copy(value = joinedValue)))
                },
            )
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    showSearch: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = HATextStyle.HeadlineMedium.copy(textAlign = TextAlign.Start),
            modifier = Modifier.padding(vertical = HADimens.SPACE3),
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
}

@Composable
private fun SheetEntryList(
    loading: Boolean,
    entries: List<SettingEntry>,
    checkedValue: SnapshotStateList<String>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(entries, key = { it.id }) { entry ->
                    BottomSheetSettingRow(
                        label = entry.label,
                        checked = checkedValue.contains(entry.id),
                        onClick = { isChecked ->
                            if (isChecked) {
                                if (entry.id !in checkedValue) checkedValue.add(entry.id)
                            } else {
                                checkedValue.remove(entry.id)
                            }
                        },
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HADimens.SPACE3),
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
internal data class SettingEntry(val id: String, val label: String)

/**
 * Computes the filtered entries off the UI thread.
 *
 * Re-runs whenever [entries] or [searchQuery] change, dispatching the filter work onto
 * [Dispatchers.Default] so long lists do not freeze the sheet.
 */
@Composable
private fun rememberFilteredSettingEntries(entries: List<SettingEntry>, searchQuery: String): List<SettingEntry> {
    var filtered by remember(entries) { mutableStateOf(entries) }

    LaunchedEffect(entries, searchQuery) {
        filtered = withContext(Dispatchers.Default) {
            filterSettingEntries(entries, searchQuery)
        }
    }

    return filtered
}

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
 * Splits a sensor setting label into a primary line and an optional secondary line.
 *
 * Sensor allow-list entries are formatted as `"<primary>\n(<secondary>)"` where the secondary
 * line is wrapped in parentheses (for example `"Chrome\n(com.google.chrome)"`). Only the first
 * newline is used to separate the two parts; any further newlines remain inside the secondary
 * value. Surrounding parentheses are stripped from the secondary value when, and only when, both
 * the leading `(` and trailing `)` are present, matching [String.removeSurrounding] semantics.
 *
 * Shared between [SensorDetailSettingSheet] (the new bottom sheet) and the legacy Material 2
 * dialog row in `SensorDetailView` so both render identical primary/secondary text.
 *
 * @return a pair of `(primary, secondary)` where `secondary` is `null` for single-line labels.
 */
internal fun parseSettingLabel(label: String): Pair<String, String?> {
    val parts = label.split("\n", limit = 2)
    val primaryText = parts[0]
    val secondaryText = parts.getOrNull(1)?.removeSurrounding("(", ")")
    return primaryText to secondaryText
}

/**
 * Returns a [NestedScrollConnection] that absorbs fling velocity and post-scroll offsets to prevent
 * the bottom sheet from collapsing while the user scrolls or flings the entry list.
 */
private fun nestedScrollFlingGuard(): NestedScrollConnection {
    return object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
    }
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
    val (primaryText, secondaryText) = parseSettingLabel(label)
    val colorScheme = LocalHAColorScheme.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(!checked) }
            .heightIn(min = HADimens.SPACE16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.size(width = HADimens.SPACE12, height = HADimens.SPACE12),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = HATextStyle.Body.copy(
                    textAlign = TextAlign.Start,
                    color = colorScheme.colorTextPrimary,
                ),
            )
            if (secondaryText != null) {
                Spacer(Modifier.height(HADimens.SPACE1))
                Text(
                    text = secondaryText,
                    style = HATextStyle.BodyMedium.copy(textAlign = TextAlign.Start),
                )
            }
        }
    }
}
