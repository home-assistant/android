package io.homeassistant.companion.android.common.data.integration

/**
 * Selects the entities of a server that belong to one of [domains] or are one of [entityIds], so
 * only their states are requested from the server. An empty filter selects every entity, as the
 * server treats it.
 */
data class EntityFilter(val domains: Set<String> = emptySet(), val entityIds: Set<String> = emptySet()) {
    /** Whether neither a domain nor an entity id is set. */
    val isEmpty: Boolean get() = domains.isEmpty() && entityIds.isEmpty()

    /** Whether [entityId] is selected by this filter. */
    fun matches(entityId: String): Boolean =
        isEmpty || entityId in entityIds || entityId.substringBefore(ENTITY_ID_SEPARATOR) in domains
}

private const val ENTITY_ID_SEPARATOR = '.'
