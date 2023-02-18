package io.homeassistant.companion.android.thread

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse

interface ThreadManager {

    /**
     * Indicates if the app on this device supports Thread credential management.
     */
    fun appSupportsThread(): Boolean

    /**
     * Indicates if the server supports Thread credential management.
     */
    suspend fun coreSupportsThread(serverId: Int): Boolean

    /**
     * Get the preferred Thread dataset from the server.
     */
    suspend fun getPreferredDatasetFromServer(serverId: Int): ThreadDatasetResponse?

    /**
     * Import the preferred Thread dataset from the server to this device.
     * @throws Exception if a preferred dataset exists on the server, but it wasn't possible to
     * import it
     */
    suspend fun importPreferredDatasetFromServer(context: Context, serverId: Int)

    /**
     * Start a flow to get the preferred Thread dataset from this device to export to the server.
     * @return [IntentSender] to ask the user for permission to share the preferred dataset, or
     * `null` if there are no datasets to import
     * @throws Exception if it is not possible to get the preferred dataset
     */
    suspend fun getThreadPreferredDatasetExport(context: Context): IntentSender?

    /**
     * Process the result from [getThreadPreferredDatasetExport]'s intent and add it to the server
     * as a new Thread dataset.
     */
    suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int)
}
