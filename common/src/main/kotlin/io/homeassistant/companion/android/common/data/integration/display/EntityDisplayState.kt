package io.homeassistant.companion.android.common.data.integration.display

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull

/**
 * State of the entity display resolution.
 */
@Immutable
sealed interface EntityDisplayState<out T : EntityDisplay> {
    /** The entities are being retrieved. */
    data object Loading : EntityDisplayState<Nothing>

    /** The entities could not be retrieved at all (for example the server is unreachable). */
    data object Error : EntityDisplayState<Nothing>

    /**
     * The entities are resolved and ready to be displayed.
     */
    data class Loaded<T : EntityDisplay>(val entitiesById: Map<String, T>) : EntityDisplayState<T> {
        constructor(entities: List<T>) : this(entities.associateBy { it.entityId })

        /** Resolved items in their resolved order. */
        val entities: Collection<T> get() = entitiesById.values

        /** Returns the resolved item for [entityId], or null when it is not in the list. */
        fun entity(entityId: String): T? = entitiesById[entityId]
    }
}

/**
 * Awaits the first [EntityDisplayState.Loaded] of the flow, for the callers resolving entities once
 * rather than observing them. The [EntityDisplayState.Loading] emitted first is skipped, and null is
 * returned when the flow completes without a loaded state (an [EntityDisplayState.Error]). Collecting
 * an observing flow stops it after the first loaded state.
 */
suspend fun <T : EntityDisplay> Flow<EntityDisplayState<T>>.awaitLoadedOrNull(): EntityDisplayState.Loaded<T>? =
    filterIsInstance<EntityDisplayState.Loaded<T>>().firstOrNull()
