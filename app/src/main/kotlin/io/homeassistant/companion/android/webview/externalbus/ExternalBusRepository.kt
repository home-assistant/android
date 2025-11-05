package io.homeassistant.companion.android.webview.externalbus

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * A repository to communicate with the external bus which is provided by the frontend,
 * in contexts where there is no 'line of sight' to the webview (usually: other activity).
 *
 * The [WebViewActivity] or listener should be alive for this to work, and the repository
 * does not guarantee that the the receiver will immediately receive the message as the
 * system can limit background activity.
 */
interface ExternalBusRepository {

    /** Send a message to the external bus (for native) */
    suspend fun send(message: ExternalBusMessage)

    /**
     * Register to receive certain messages from the external bus (for native)
     * @param types List of which message `type`s should be received
     * @return Flow with received messages for the specified types
     */
    fun receive(types: List<String>): Flow<JsonObject>

    /** Send a message from the external bus to registered receivers (for webview) */
    suspend fun received(message: JsonObject)

    /**
     * @return Flow with [ExternalBusMessage]s that should be sent on the external
     * bus (for webview)
     */
    fun getSentFlow(): Flow<ExternalBusMessage>
}
