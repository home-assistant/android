package io.homeassistant.companion.android.thread

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import kotlinx.coroutines.CoroutineScope

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
     * Try to sync the preferred Thread dataset with the device and server. If one has a preferred
     * dataset while the other one doesn't, it will sync. If both have preferred datasets, it will
     * send updated data to the server if needed. If neither has a preferred dataset, skip syncing.
     * @return [IntentSender] if permission is required to import the device dataset, `null` if
     * syncing completed or there is nothing to sync
     */
    suspend fun syncPreferredDataset(
        context: Context,
        serverId: Int,
        scope: CoroutineScope
    ): IntentSender?

    /**
     * Get the preferred Thread dataset from the server.
     */
    suspend fun getPreferredDatasetFromServer(serverId: Int): ThreadDatasetResponse?

    /**
     * Import a Thread dataset from the server to this device.
     * @param datasetId The dataset ID as provided by the server
     * @throws Exception if a preferred dataset exists on the server, but it wasn't possible to
     * import it
     */
    suspend fun importDatasetFromServer(context: Context, datasetId: String, serverId: Int)

    /**
     * Start a flow to get the preferred Thread dataset from this device to export to the server.
     * @return [IntentSender] to ask the user for permission to share the preferred dataset, or
     * `null` if there are no datasets to import
     * @throws Exception if it is not possible to get the preferred dataset
     */
    suspend fun getPreferredDatasetFromDevice(context: Context): IntentSender?

    /**
     * Process the result from [syncPreferredDataset] or [getPreferredDatasetFromDevice]'s intent
     * and add the Thread dataset, if any, to the server.
     */
    suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int)
}
