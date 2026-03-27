package io.homeassistant.companion.android.common.data.mediacontrol

import kotlinx.coroutines.flow.Flow

/**
 * Manages configuration and state observation for media_player entities
 * exposed as native Android media controls in the notification shade.
 */
interface MediaControlRepository {

    /**
     * Returns the current [MediaControlState] for a single entity via a one-shot REST fetch,
     * or null if the entity is unavailable or the request fails.
     */
    suspend fun getEntityState(config: MediaControlEntityConfig): MediaControlState?

    /**
     * Emits the current [MediaControlState] for a single entity whenever its state changes.
     * Emits null when the entity is unavailable or the WebSocket subscription fails.
     */
    fun observeEntityState(config: MediaControlEntityConfig): Flow<MediaControlState?>

    /** Returns the list of all configured media_player entities. */
    suspend fun getConfiguredEntities(): List<MediaControlEntityConfig>

    /** Replaces the full list of configured media_player entities. */
    suspend fun setConfiguredEntities(entities: List<MediaControlEntityConfig>)
}
