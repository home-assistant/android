package io.homeassistant.companion.android.common.compose.composable

import androidx.annotation.VisibleForTesting
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** Default debounce applied before propagating non-empty queries to the parent. */
private val DEFAULT_DEBOUNCE = 300.milliseconds

/**
 * Forwards [rawQuery] to [emit], debouncing non-empty queries by [debounce].
 *
 * Empty strings bypass the delay so clearing the field reflects instantly to the parent. For
 * non-empty queries the function suspends for [debounce] before emitting; if the surrounding
 * coroutine is cancelled (e.g. because the raw query changed again) the pending emit is dropped,
 * which is exactly the coalescing behaviour expected from a search debounce.
 *
 * Extracted from [HASearchField]'s [LaunchedEffect] so the timing rules can be tested without a
 * Compose host.
 */
@VisibleForTesting
internal suspend fun debouncedSearchUpdate(
    rawQuery: String,
    debounce: Duration,
    emit: (String) -> Unit,
) {
    if (rawQuery.isEmpty()) {
        emit(rawQuery)
    } else {
        delay(debounce)
        emit(rawQuery)
    }
}

/**
 * Reusable search input field for filtering lists.
 *
 * Holds the raw text locally and propagates changes to [onQueryChange] with a [debounce] delay,
 * except for empty queries which are forwarded immediately to provide instant clear feedback when
 * the user wipes the field. Local state is also kept in sync with [query] so external changes
 * (for example a parent clearing the search) are reflected in the field.
 *
 * The field is rendered as an [HATextField] with the standard "Search" label and a trailing clear
 * [IconButton] that appears only when the current text is non-empty. An optional [leadingIcon] slot
 * allows callers to render a search glyph or other adornment without changing the debounce logic.
 *
 * @param query Current query coming from the parent state. Used to seed and re-sync local state.
 * @param onQueryChange Callback invoked with the new query, debounced for non-empty values.
 * @param modifier The [Modifier] to be applied to this search field.
 * @param leadingIcon Optional leading content rendered inside the text field.
 * @param debounce Delay before non-empty queries are propagated to [onQueryChange].
 */
@Composable
fun HASearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    debounce: Duration = DEFAULT_DEBOUNCE,
) {
    val colorScheme = LocalHAColorScheme.current
    var searchQueryRaw by remember { mutableStateOf(query) }

    // Sync local state when parent state changes (e.g., when cleared externally).
    LaunchedEffect(query) {
        if (query != searchQueryRaw) {
            searchQueryRaw = query
        }
    }

    // Debounced update to parent. Empty strings propagate immediately to keep clearing snappy.
    LaunchedEffect(searchQueryRaw) {
        debouncedSearchUpdate(rawQuery = searchQueryRaw, debounce = debounce, emit = onQueryChange)
    }

    HATextField(
        value = searchQueryRaw,
        onValueChange = { searchQueryRaw = it },
        label = { Text(stringResource(commonR.string.search)) },
        leadingIcon = leadingIcon,
        trailingIcon = {
            if (searchQueryRaw.isNotEmpty()) {
                IconButton(onClick = { searchQueryRaw = "" }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(commonR.string.clear_search),
                        tint = colorScheme.colorOnNeutralNormal,
                    )
                }
            }
        },
        modifier = modifier,
    )
}
