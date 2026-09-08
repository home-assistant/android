package io.homeassistant.companion.android.common.compose.composable

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.theme.LocalHAColorScheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** Default debounce applied before propagating non-empty text to [SearchFieldState.query]. */
@VisibleForTesting
internal val SEARCH_FIELD_DEFAULT_DEBOUNCE = 300.milliseconds

/**
 * State holder of an [HASearchField], owning both the live text and the debounced query so
 * consumers do not need to hoist and resynchronize the search value themselves.
 *
 * @param initialQuery The query the field starts with
 * @param debounce Delay before non-empty raw text is propagated to [query]
 */
@Stable
class SearchFieldState(initialQuery: String = "", internal val debounce: Duration = SEARCH_FIELD_DEFAULT_DEBOUNCE) {
    // Raw text as typed in the field, updated on every keystroke. Internal because consumers
    // should filter on the debounced [query], not the raw text.
    internal var rawText by mutableStateOf(initialQuery)

    /**
     * Debounced query, updated [debounce] after the last keystroke (immediately when the field
     * is cleared). Consumers should filter on this value.
     */
    var query by mutableStateOf(initialQuery)
        internal set

    /** Clears the field, immediately resetting both [rawText] and [query]. */
    fun clear() {
        rawText = ""
        query = ""
    }
}

/**
 * Creates and remembers a [SearchFieldState].
 *
 * @param initialQuery The query the field starts with
 * @param debounce Delay before non-empty text is propagated to [SearchFieldState.query]
 */
@Composable
fun rememberSearchFieldState(
    initialQuery: String = "",
    debounce: Duration = SEARCH_FIELD_DEFAULT_DEBOUNCE,
): SearchFieldState = remember { SearchFieldState(initialQuery = initialQuery, debounce = debounce) }

/**
 * Reusable search input field driven by a [SearchFieldState].
 *
 * The field renders and edits [SearchFieldState.rawText] and propagates it to
 * [SearchFieldState.query] with the state's debounce, except for empty text which is forwarded
 * immediately to provide instant clear feedback when the user wipes the field. Consumers read
 * [SearchFieldState.query] to filter their content and can reset the field with
 * [SearchFieldState.clear].
 *
 * The field is rendered as an [HATextField] with the standard "Search" label and a trailing
 * clear [IconButton] that appears only when the current text is non-empty. An optional
 * [leadingIcon] slot allows callers to render a search glyph or other adornment.
 *
 * @param modifier The [Modifier] to be applied to this search field.
 * @param state The state holder of the field, see [rememberSearchFieldState].
 * @param leadingIcon Optional leading content rendered inside the text field.
 */
@Composable
fun HASearchField(
    modifier: Modifier = Modifier,
    state: SearchFieldState = rememberSearchFieldState(),
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val colorScheme = LocalHAColorScheme.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Debounced propagation of the raw text to the query. A new keystroke restarts the
    // effect, dropping the pending update, which is the coalescing expected from a search
    // debounce. Empty text propagates immediately to keep clearing snappy.
    LaunchedEffect(state.rawText) {
        val rawText = state.rawText
        if (rawText.isNotEmpty()) {
            delay(state.debounce)
        }
        state.query = rawText
    }

    HATextField(
        value = state.rawText,
        onValueChange = { state.rawText = it },
        label = { Text(stringResource(commonR.string.search)) },
        leadingIcon = leadingIcon,
        trailingIcon = {
            if (state.rawText.isNotEmpty()) {
                IconButton(onClick = { state.clear() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(commonR.string.clear_search),
                        tint = colorScheme.colorOnNeutralNormal,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
            },
        ),
        singleLine = true,
        modifier = modifier,
    )
}
