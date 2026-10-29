package io.homeassistant.companion.android.common.data.integration

/** Change the server reports for an observed entity. */
sealed interface EntityChange {
    /** The entity has a new state. */
    data class StateChanged(val entity: Entity) : EntityChange

    /** The entity was removed from the server. */
    data class Removed(val entityId: String) : EntityChange
}
