package io.homeassistant.companion.android.tiles

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.data.SimplifiedEntity

/**
 * Pairs of reciprocal states that a toggle tap is expected to swap between. If the entity's
 * current state matches one side of a pair for its domain, the tap is predicted to produce
 * the other side.
 *
 * Why: `requestUpdate()` after a WebSocket state change does not reliably trigger the Wear
 * tile framework to re-render, so users see the previous state until an unrelated event
 * forces a refresh. Predicting the post-click state in `onTileRequest` makes the tap feel
 * immediate. If the server ends up in a different state (e.g. a lock stuck mid-transition),
 * the subscription will overwrite the cache and the display converges next refresh.
 */
private val TOGGLE_STATE_PAIRS: Map<String, Pair<String, String>> = mapOf(
    "switch" to ("on" to "off"),
    "light" to ("on" to "off"),
    "input_boolean" to ("on" to "off"),
    "fan" to ("on" to "off"),
    "lock" to ("locked" to "unlocked"),
    "cover" to ("open" to "closed"),
)

/**
 * Returns the state an entity is expected to be in after a single click, or null if the
 * domain isn't a known toggle or the current state isn't a recognized side of its pair.
 * Conservative on purpose: unknown inputs leave the cache alone.
 */
internal fun predictedStateAfterClick(entity: Entity): String? {
    val pair = TOGGLE_STATE_PAIRS[entity.domain] ?: return null
    return when (entity.state) {
        pair.first -> pair.second
        pair.second -> pair.first
        else -> null
    }
}

/**
 * Returns a copy of [entity] with its state flipped per [predictedStateAfterClick], or the
 * input unchanged if no prediction is possible.
 */
internal fun applyOptimisticClick(entity: Entity): Entity {
    val predicted = predictedStateAfterClick(entity) ?: return entity
    return entity.copy(state = predicted)
}

/**
 * Frozen view of entity states used to render a single tile response.
 *
 * `onTileRequest` and `onTileResourcesRequest` are separate framework calls that both need
 * a consistent view of entity state. If each reads the live cache independently, a WebSocket
 * update in between can cause the layout to reference a resource ID that the resource bundle
 * never generated — producing stale or missing bitmaps on screen.
 *
 * A snapshot is built at `onTileRequest` time, stashed by resources version, and looked up
 * again when the matching `onTileResourcesRequest` arrives.
 */
internal data class TileSnapshot(val entityStates: Map<String, Entity?>) {
    /** State string for an entity, or null if the cache has no entry. */
    fun stateOf(entityId: String): String? = entityStates[entityId]?.state

    fun entityOf(entityId: String): Entity? = entityStates[entityId]

    companion object {
        /**
         * Take a snapshot from a live cache, copying values for the given entity IDs.
         * Missing entries are included as null so the snapshot is complete for the tile.
         */
        fun from(liveCache: Map<String, Entity>, entityIds: Iterable<String>): TileSnapshot =
            TileSnapshot(entityIds.associateWith { liveCache[it] })
    }
}

/**
 * Resource ID used in the tile layout. The state suffix ensures Wear OS treats different
 * states as distinct resources, preventing the bitmap cache from serving a stale icon when
 * the state changes.
 */
internal fun resourceIdForEntity(entityId: String, state: String?): String =
    if (state.isNullOrEmpty()) entityId else "$entityId@$state"

/** Resource ID for an entity in the given snapshot. */
internal fun SimplifiedEntity.resourceIdIn(snapshot: TileSnapshot): String =
    resourceIdForEntity(entityId, snapshot.stateOf(entityId))

/**
 * The set of resource IDs required by a tile layout for the given entities and snapshot.
 * Used as the invariant: every ID the layout references must be present in the resource bundle.
 */
internal fun requiredResourceIds(entities: List<SimplifiedEntity>, snapshot: TileSnapshot): Set<String> =
    entities.map { it.resourceIdIn(snapshot) }.toSet()

/**
 * Bounded cache of snapshots keyed by resources version string.
 *
 * Wear OS may request resources for an older version than the latest tile render, especially
 * when multiple `requestUpdate()` calls arrive quickly. Keeping the last few snapshots lets
 * those requests resolve to the correct frozen state instead of the current live cache.
 */
internal class SnapshotStash(private val maxEntries: Int = 4) {
    private val entries = linkedMapOf<String, TileSnapshot>()

    @Synchronized
    fun put(version: String, snapshot: TileSnapshot) {
        entries.remove(version)
        entries[version] = snapshot
        while (entries.size > maxEntries) {
            val oldest = entries.keys.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
    }

    @Synchronized
    fun get(version: String): TileSnapshot? = entries[version]

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(): Int = entries.size
}
