package io.homeassistant.companion.android.common.data.integration.display

import androidx.compose.runtime.Immutable

/**
 * State of the entity display resolution.
 */
@Immutable
sealed interface EntityDisplayState {
    /** The entities are being retrieved. */
    data object Loading : EntityDisplayState

    /** The entities could not be retrieved at all (for example the server is unreachable). */
    data object Error : EntityDisplayState

    /** The entities are resolved and ready to be displayed. */
    data class Loaded(val entities: List<EntityDisplayItem>) : EntityDisplayState
}
