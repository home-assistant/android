package io.homeassistant.companion.android.common.data.mediacontrols

import kotlinx.coroutines.flow.Flow

/**
 * Stores which media_player entities are exposed as native Android media controls in the
 * notification shade. Their state is followed through
 * [io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager].
 */
interface MediaControlsRepository {

    /** Returns the list of all configured media_player entities. */
    suspend fun getEntities(): List<MediaControlsEntityConfig>

    /** Emits the list of configured entities whenever it changes. */
    fun observeEntities(): Flow<List<MediaControlsEntityConfig>>

    suspend fun addEntity(entity: MediaControlsEntityConfig)

    suspend fun removeEntity(entity: MediaControlsEntityConfig)
}
