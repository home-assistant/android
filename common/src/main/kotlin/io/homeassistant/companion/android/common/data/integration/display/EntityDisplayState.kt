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

    /**
     * The entities are resolved and ready to be displayed.
     */
    data class Loaded(val entitiesById: Map<String, EntityDisplayItem>) : EntityDisplayState {
        constructor(entities: List<EntityDisplayItem>) : this(entities.associateBy { it.entityId })

        /** Resolved items in their resolved order. */
        val entities: Collection<EntityDisplayItem> get() = entitiesById.values

        /** Returns the resolved item for [entityId], or null when it is not in the list. */
        fun entity(entityId: String): EntityDisplayItem? = entitiesById[entityId]
    }
}
