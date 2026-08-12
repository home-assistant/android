package io.homeassistant.companion.android.util.compose.entity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.homeassistant.companion.android.common.compose.composable.SearchFieldState
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * UI state holder of an [EntityPicker], owning the mechanics of the component (expansion of
 * the sheet/dropdown, the search field, and where the search filtering work runs) so callers
 * and tests can control them programmatically, similar to Material state holders like
 * `SheetState`.
 *
 * The entity selection deliberately stays out of this holder: it is application state owned
 * by the caller and hoisted through the `selectedEntityId`/`onSelectionChanged` parameters.
 */
@Stable
class EntityPickerState internal constructor(
    isExpanded: Boolean,
    /** State of the search field, exposing the debounced [SearchFieldState.query] to filter on. */
    internal val search: SearchFieldState = SearchFieldState(),
    /** Coroutine context running the search filtering work. */
    internal val dispatcher: CoroutineContext = Dispatchers.Default,
) {
    /** Whether the picker currently shows its search sheet (phones) or dropdown (tablets). */
    var isExpanded by mutableStateOf(isExpanded)
        private set

    /** Opens the search sheet/dropdown. */
    fun expand() {
        isExpanded = true
    }

    /** Closes the search sheet/dropdown and resets the search field. */
    fun collapse() {
        isExpanded = false
        search.clear()
    }
}

/**
 * Creates and remembers an [EntityPickerState].
 *
 * @param isExpanded Whether the picker starts expanded, mainly useful for previews
 */
@Composable
fun rememberEntityPickerState(isExpanded: Boolean = false): EntityPickerState =
    remember { EntityPickerState(isExpanded = isExpanded) }
