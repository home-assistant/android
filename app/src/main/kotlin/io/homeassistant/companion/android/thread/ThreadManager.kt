package io.homeassistant.companion.android.thread

import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import kotlinx.coroutines.CoroutineScope

interface ThreadManager {

    /**
     * Outcome of a Thread credential sync between the device and the server.
     */
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

    /**
     * Indicates if the app on this device supports Thread credential management.
     */
    fun appSupportsThread(): Boolean

    /**
     * Indicates if the server supports Thread credential management.
     */
    suspend fun coreSupportsThread(serverId: Int): Boolean

    /**
     * Prepare the device's preferred Thread dataset for one-way export to the server.
     *
     * Returns a [SyncResult]; the export-only path produces a subset of variants:
     *   - [SyncResult.OnlyOnDevice] with a non-null `exportIntent` — launch it and pass the
     *     [ActivityResult] back to [sendThreadDatasetExportResult] to complete the export.
     *   - [SyncResult.NoneHaveCredentials] or [SyncResult.OnlyOnServer] with `imported = false`
     *     — nothing new on the device to share.
     *   - [SyncResult.NotConnected] — device is not on the local network.
     *   - [SyncResult.AppUnsupported] / [SyncResult.ServerUnsupported] — support gates failed.
     *
     * [SyncResult.OnlyOnServer] with `imported = true` and [SyncResult.AllHaveCredentials] are
     * full-sync-only outcomes and won't appear here.
     */
    suspend fun exportThreadCredentials(serverId: Int): SyncResult

    /**
     * Run a full bidirectional sync of the preferred Thread dataset between the device and the
     * server: cleans up stale app-added datasets, imports from one side to the other when only
     * one has a credential, and reconciles when both do.
     *
     * @return [SyncResult] with details of the sync operation, which may include an [IntentSender]
     * if permission is required to import the device dataset.
     */
    suspend fun syncPreferredDataset(serverId: Int, scope: CoroutineScope): SyncResult

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
    suspend fun importDatasetFromServer(datasetId: String, preferredBorderAgentId: String?, serverId: Int)

    /**
     * Start a flow to get the preferred Thread dataset from this device to export to the server.
     * @return [IntentSender] to ask the user for permission to share the preferred dataset, or
     * `null` if there are no datasets to import
     * @throws Exception if it is not possible to get the preferred dataset
     */
    suspend fun getPreferredDatasetFromDevice(): IntentSender?

    /**
     * Process the result from [exportThreadCredentials] or [getPreferredDatasetFromDevice]'s
     * intent and add the Thread dataset, if any, to the server.
     * @return Network name that was sent and accepted, or `null` if not sent or accepted
     */
    suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int): String?
}
