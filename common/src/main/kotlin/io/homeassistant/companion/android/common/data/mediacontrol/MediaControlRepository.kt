package io.homeassistant.companion.android.common.data.mediacontrol

import kotlinx.coroutines.flow.Flow

/**
 * Manages the configuration and state observation for a single media_player entity
 * used to drive native Android media controls in the notification shade.
 */
interface MediaControlRepository {

    /** Emits the current [MediaControlState] whenever the configured entity's state changes. Emits null when not configured. */
    fun observeMediaControlState(): Flow<MediaControlState?>

    /** Returns the currently configured server ID, or null if not configured. */
    suspend fun getConfiguredServerId(): Int?

    /** Returns the currently configured entity ID, or null if not configured. */
    suspend fun getConfiguredEntityId(): String?

    /** Sets the configured media_player entity. Pass null values to clear the configuration. */
    suspend fun setConfiguredEntity(serverId: Int?, entityId: String?)
}
