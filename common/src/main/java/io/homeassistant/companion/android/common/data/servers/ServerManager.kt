package io.homeassistant.companion.android.common.data.servers

import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerType

interface ServerManager {

    companion object {
        const val SERVER_ID_ACTIVE = -1
    }

    val servers: List<Server>

    /**
     * @return `true` if the app is registered with any server
     */
    fun isRegistered(): Boolean

    /**
     * Add a new server to the manager, and if the [ServerType] is not temporary also to the database.
     * @return ID for the added server
     */
    suspend fun addServer(server: Server): Int

    /**
     * Get the server for the provided ID
     * @return [Server] or `null` if there is no server for the ID
     */
    fun getServer(id: Int = SERVER_ID_ACTIVE): Server?

    /**
     * Update a server based on the provided object
     */
    fun updateServer(server: Server)

    /**
     * Convert a temporary server in the manager to a default server
     * @return ID for the added server, or null if the server wasn't converted
     */
    suspend fun convertTemporaryServer(id: Int): Int?

    /**
     * Remove the server for the provided ID from the manager and if required the database, and
     * clean up all related resources for it
     */
    suspend fun removeServer(id: Int)

    /**
     * @return [AuthenticationRepository] for the server with the provided ID
     * @throws [IllegalArgumentException] if there is no server with the provided ID
     */
    fun authenticationRepository(serverId: Int = SERVER_ID_ACTIVE): AuthenticationRepository

    /**
     * @return [IntegrationRepository] for the server with the provided ID
     * @throws [IllegalArgumentException] if there is no server with the provided ID
     */
    fun integrationRepository(serverId: Int = SERVER_ID_ACTIVE): IntegrationRepository

    /**
     * @return [WebSocketRepository] for the server with the provided ID
     * @throws [IllegalArgumentException] if there is no server with the provided ID
     */
    fun webSocketRepository(serverId: Int = SERVER_ID_ACTIVE): WebSocketRepository
}
