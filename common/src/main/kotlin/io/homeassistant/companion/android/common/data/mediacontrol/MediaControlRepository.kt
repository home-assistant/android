package io.homeassistant.companion.android.common.data.mediacontrol

import kotlinx.coroutines.flow.Flow

/**
 * Manages configuration and state observation for media_player entities
 * exposed as native Android media controls in the notification shade.
 */
interface MediaControlRepository {

    /**
     * Emits the current [MediaControlState] for a single entity,
     * then continues emitting whenever its state changes.
     * Emits null when the entity is unavailable.
     */
    fun observeEntityState(config: MediaControlEntityConfig): Flow<MediaControlState?>

    /** Returns the list of all configured media_player entities. */
    suspend fun getConfiguredEntities(): List<MediaControlEntityConfig>

    /** Emits the list of configured entities whenever it changes. */
    fun observeConfiguredEntities(): Flow<List<MediaControlEntityConfig>>

    /** Replaces the full list of configured media_player entities. */
    suspend fun setConfiguredEntities(entities: List<MediaControlEntityConfig>)
}
