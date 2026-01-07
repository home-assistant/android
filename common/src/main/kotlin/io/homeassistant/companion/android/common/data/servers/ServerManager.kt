package io.homeassistant.companion.android.common.data.servers

import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager.Companion.SERVER_ID_ACTIVE
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.TemporaryServer
import kotlinx.coroutines.flow.Flow

interface ServerManager {

    companion object {
        const val SERVER_ID_ACTIVE = -1
    }

    /**
     * Returns a list of all registered [Server]s.
     */
    suspend fun servers(): List<Server>

    /**
     * A [Flow] that emits the list of all registered [Server]s whenever it changes.
     */
    val serversFlow: Flow<List<Server>>

    /**
     * @return `true` if the app is registered with any server
     */
    suspend fun isRegistered(): Boolean

    /**
     * Persists a [TemporaryServer] as a registered [Server].
     *
     * @param server the temporary server configuration to persist
     * @return unique ID for the added server
     */
    suspend fun addServer(server: TemporaryServer): Int

    /**
     * Gets the server for the provided ID, or the currently active server if [SERVER_ID_ACTIVE].
     *
     * @param id the server ID, or [SERVER_ID_ACTIVE] to get the currently active server
     * @return [Server] or `null` if there is no server for the ID
     */
    suspend fun getServer(id: Int = SERVER_ID_ACTIVE): Server?

    /**
     * Gets the server for the provided webhook ID.
     *
     * @param webhookId the webhook ID to look up
     * @return [Server] or `null` if there is no server for the webhook ID
     */
    suspend fun getServer(webhookId: String): Server?

    /**
     * Marks the server for the provided ID as 'active'. The active server is used as the default
     * when [SERVER_ID_ACTIVE] is passed to other methods. This setting persists across app restarts.
     *
     * @param id the server ID to activate
     */
    suspend fun activateServer(id: Int)

    /**
     * Updates a server's configuration in the database.
     *
     * @param server the server with updated values; matched by [Server.id]
     */
    suspend fun updateServer(server: Server)

    /**
     * Removes the server for the provided ID from the database and cleans up all related resources
     * such as authentication tokens and cached data.
     *
     * @param id the server ID to remove
     */
    suspend fun removeServer(id: Int)

    /**
     * Gets the [AuthenticationRepository] for the specified server.
     *
     * @param serverId the server ID, or [SERVER_ID_ACTIVE] for the currently active server
     * @return [AuthenticationRepository] for the server
     * @throws IllegalArgumentException if there is no server with the provided ID
     */
    suspend fun authenticationRepository(serverId: Int = SERVER_ID_ACTIVE): AuthenticationRepository

    /**
     * Gets the [IntegrationRepository] for the specified server.
     *
     * @param serverId the server ID, or [SERVER_ID_ACTIVE] for the currently active server
     * @return [IntegrationRepository] for the server
     * @throws IllegalArgumentException if there is no server with the provided ID
     */
    suspend fun integrationRepository(serverId: Int = SERVER_ID_ACTIVE): IntegrationRepository

    /**
     * Gets the [WebSocketRepository] for the specified server.
     *
     * @param serverId the server ID, or [SERVER_ID_ACTIVE] for the currently active server
     * @return [WebSocketRepository] for the server
     * @throws IllegalArgumentException if there is no server with the provided ID
     */
    suspend fun webSocketRepository(serverId: Int = SERVER_ID_ACTIVE): WebSocketRepository

    /**
     * Gets the [ServerConnectionStateProvider] for the specified server.
     *
     * @param serverId the server ID, or [SERVER_ID_ACTIVE] for the currently active server
     * @return [ServerConnectionStateProvider] for the server
     * @throws IllegalArgumentException if there is no server with the provided ID
     */
    suspend fun connectionStateProvider(serverId: Int = SERVER_ID_ACTIVE): ServerConnectionStateProvider
}
