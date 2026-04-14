package io.homeassistant.companion.android.frontend.js

import io.homeassistant.companion.android.frontend.session.AuthPayload
import kotlinx.serialization.json.JsonElement

/**
 * Handler interface for processing messages from the Home Assistant frontend.
 */
interface FrontendJsHandler {
    /**
     * Called when the frontend requests an authentication token.
     *
     * The callback name has already been validated by the bridge before this is called.
     *
     * @param authPayload Parsed authentication payload with callback name and force flag
     * @param serverId The server ID to authenticate against
     */
    suspend fun getExternalAuth(authPayload: AuthPayload, serverId: Int)

    /**
     * Called when the frontend requests to revoke the authentication session.
     *
     * The callback name has already been validated by the bridge before this is called.
     *
     * @param authPayload Parsed authentication payload with callback name
     * @param serverId The server ID whose session should be revoked
     */
    suspend fun revokeExternalAuth(authPayload: AuthPayload, serverId: Int)

    /**
     * Called when the frontend sends a message through the external bus.
     *
     * @param message Already-parsed JSON element containing the bus message
     */
    suspend fun externalBus(message: JsonElement)
}
