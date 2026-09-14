package io.homeassistant.companion.android.common.data.mediacontrol

import kotlinx.coroutines.flow.Flow

/**
 * Stores which media_player entities are exposed as native Android media controls in the
 * notification shade. Their state is followed through
 * [io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager].
 */
interface MediaControlRepository {

    /** Returns the list of all configured media_player entities. */
    suspend fun getConfiguredEntities(): List<MediaControlEntityConfig>

    /** Emits the list of configured entities whenever it changes. */
    fun observeConfiguredEntities(): Flow<List<MediaControlEntityConfig>>

    suspend fun addConfiguredEntity(entity: MediaControlEntityConfig)

    suspend fun removeConfiguredEntity(entity: MediaControlEntityConfig)
}
