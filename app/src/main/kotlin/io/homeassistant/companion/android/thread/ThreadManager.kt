package io.homeassistant.companion.android.thread

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import kotlinx.coroutines.CoroutineScope

interface ThreadManager {

    sealed class SyncResult {
        object AppUnsupported : SyncResult()
        object ServerUnsupported : SyncResult()
        object NotConnected : SyncResult()
        class OnlyOnServer(val imported: Boolean) : SyncResult()
        class OnlyOnDevice(val exportIntent: IntentSender?) : SyncResult()
        class AllHaveCredentials(
            val matches: Boolean?,
            val fromApp: Boolean?,
            val updated: Boolean?,
            val exportIntent: IntentSender?,
        ) : SyncResult()
        object NoneHaveCredentials : SyncResult()
    }

    sealed class ExportResult {
        /**
         * The activity result was not OK, the server doesn't support Thread, or there was no
         * dataset to send.
         */
        object NotSent : ExportResult()

        /** Sending the dataset to the server failed. */
        object Failed : ExportResult()

        /**
         * The dataset was added on the server.
         * @param networkName Network name of the dataset that was sent
         * @param serverPrefersExported `true` if the server now reports the exported dataset as
         * its preferred one, `false` if the server still prefers a different dataset (typically
         * because a Thread border router managed by the server is announcing one), or `null` if
         * the post-export state could not be determined.
         */
        data class Sent(val networkName: String, val serverPrefersExported: Boolean?) : ExportResult()
    }

    /**
     * Indicates if the app on this device supports Thread credential management.
     */
    fun appSupportsThread(): Boolean

    /**
     * Indicates if the server supports Thread credential management.
     */
    suspend fun coreSupportsThread(serverId: Int): Boolean

    /**
     * Try to sync the preferred Thread dataset.
     * @param exportOnly Controls the synchronization direction.
     *  - If set to `true`, only get the device preferred dataset and sync to the server if it
     *    wasn't added by the app.
     *  - If set to `false`, try to get the device and server in sync. This will clean up old/stale
     *    app datasets. If one has a preferred dataset while the other one doesn't, it will sync to
     *    the other. If both have preferred datasets, it will send updated data to the server if
     *    needed. If neither has a preferred dataset, skip syncing.
     * @return [SyncResult] with details of the sync operation, which may include an [IntentSender]
     * if permission is required to import the device dataset
     */
    suspend fun syncPreferredDataset(
        context: Context,
        serverId: Int,
        exportOnly: Boolean,
        scope: CoroutineScope,
    ): SyncResult

    /**
     * Get the preferred Thread dataset from the server.
     */
    suspend fun getPreferredDatasetFromServer(serverId: Int): ThreadDatasetResponse?

    /**
     * Import a Thread dataset from the server to this device.
     * @param datasetId The dataset ID as provided by the server
     * @param preferredBorderAgentId The ID for the border agent that provides the dataset
     * @throws Exception if a preferred dataset exists on the server, but it wasn't possible to
     * import it
     */
    suspend fun importDatasetFromServer(
        context: Context,
        datasetId: String,
        preferredBorderAgentId: String?,
        serverId: Int,
    )

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
     *
     * The `thread/add_dataset_tlv` server command never promotes a dataset to preferred. After a
     * successful add the server is queried again to determine the post-export state: if the
     * server has no preferred dataset, the just-exported one is promoted; if it has another
     * preferred dataset (typically because a server-managed border router announces it), no
     * override is performed.
     *
     * @return [ExportResult] describing whether the dataset was sent and, if so, whether the
     * server now prefers it
     */
    suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int): ExportResult
}
