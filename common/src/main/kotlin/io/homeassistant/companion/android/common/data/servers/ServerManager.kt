package io.homeassistant.companion.android.common.data.servers

import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerType
import kotlinx.coroutines.flow.StateFlow

interface ServerManager {

    companion object {
        const val SERVER_ID_ACTIVE = -1
    }

    /**
     * A list of all [Server]s managed by the app of the type [ServerType.DEFAULT].
     */
    val defaultServers: List<Server>

    /**
     * A [StateFlow] that holds the current list of all [Server]s managed by the app of the type
     * [ServerType.DEFAULT].
     */
    val defaultServersFlow: StateFlow<List<Server>>

    /**
     * @return `true` if the app is registered with any server
     */
    suspend fun isRegistered(): Boolean

    /**
     * Add a new server to the manager, and if the [ServerType] is not temporary also to the database.
     * @return ID for the added server
     */
    suspend fun addServer(server: Server): Int

    /**
     * Get the server for the provided ID.
     * @return [Server] or `null` if there is no server for the ID
     */
    suspend fun getServer(id: Int = SERVER_ID_ACTIVE): Server?

    /**
     * Get the server for the provided webhook ID.
     * @return [Server] or `null` if there is no server for the webhook ID
     */
    suspend fun getServer(webhookId: String): Server?

    /**
     * Mark the server for the provided ID as 'active', the default to use when no specific ID is
     * provided. Only IDs for servers with the type [ServerType.DEFAULT] are accepted, other IDs are
     * ignored.
     */
    fun activateServer(id: Int)

    /**
     * Update a server based on the provided object.
     */
    fun updateServer(server: Server)

    /**
     * Convert a temporary server in the manager to a default server.
     * @return ID for the added server, or null if the server wasn't converted
     */
    suspend fun convertTemporaryServer(id: Int): Int?

    /**
     * Remove the server for the provided ID from the manager and if required the database, and
     * clean up all related resources for it.
     */
    suspend fun removeServer(id: Int)

    /**
     * @return [AuthenticationRepository] for the server with the provided ID
     * @throws [IllegalArgumentException] if there is no server with the provided ID
     */
    suspend fun authenticationRepository(serverId: Int = SERVER_ID_ACTIVE): AuthenticationRepository

    /**
     * @return [IntegrationRepository] for the server with the provided ID
     * @throws [IllegalArgumentException] if there is no server with the provided ID
     */
    suspend fun integrationRepository(serverId: Int = SERVER_ID_ACTIVE): IntegrationRepository

    /**
     * @return [WebSocketRepository] for the server with the provided ID
     * @throws [IllegalArgumentException] if there is no server with the provided ID
     */
    suspend fun webSocketRepository(serverId: Int = SERVER_ID_ACTIVE): WebSocketRepository
}
