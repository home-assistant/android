package io.homeassistant.companion.android.thread

import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import kotlinx.coroutines.CoroutineScope

interface ThreadManager {

    /**
     * Outcome of a Thread credential sync between the device and the server.
     *
     * The hierarchy is layered so the return type itself expresses what each operation can
     * produce:
     *  - [ExportResult] is the subset reachable by the one-way [exportPreferredDataset].
     *  - [AllHaveCredentials] is only reachable by the full bidirectional [syncPreferredDataset],
     *    so it sits directly under [SyncResult] and outside [ExportResult].
     */
    sealed class SyncResult {

        /**
         * Subset of [SyncResult] that the export-only [exportPreferredDataset] can return.
         * Callers handling an [ExportResult] never have to consider [AllHaveCredentials].
         */
        sealed class ExportResult : SyncResult()

        /** The app/device does not support Thread credential management (SDK < O_MR1, Automotive, or minimal flavor). */
        object AppUnsupported : ExportResult()

        /** The server does not support Thread credential management. */
        object ServerUnsupported : ExportResult()

        /** Reports the device is not connected to the local network. */
        object NotConnected : ExportResult()

        /**
         * The server holds the authoritative preferred dataset and there is nothing new for the
         * device to contribute.
         *
         * This is distinct from [NoneHaveCredentials]: here the device *does* have a preferred
         * Thread credential, but it was contributed by this app (so it already mirrors the
         * server's), whereas [NoneHaveCredentials] means the device has no Thread credential at
         * all. Callers may want to surface these two cases differently — e.g. "already shared"
         * versus "no Thread network found".
         *
         * @param imported `true` if the server's dataset was just imported onto this device
         *   during a full sync; the export-only path always reports `false`.
         */
        class OnlyOnServer(val imported: Boolean) : ExportResult()

        /**
         * Only the device has a preferred dataset to share with the server.
         * @param exportIntent [IntentSender] to launch for the user to grant permission to share
         *   it, or `null` if there is nothing to share. After launching, pass the result to
         *   [sendThreadDatasetExportResult] to complete the export.
         */
        class OnlyOnDevice(val exportIntent: IntentSender?) : ExportResult()

        /**
         * Neither the device nor the server has any preferred Thread credential — there is no
         * Thread network to sync. Distinct from [OnlyOnServer], where the device holds an
         * app-contributed credential that already matches the server.
         */
        object NoneHaveCredentials : ExportResult()

        /**
         * Both the device and the server have a preferred dataset; the full sync reconciled them.
         * Only produced by [syncPreferredDataset].
         * @param matches `true` if the device already prefers the server's dataset.
         * @param fromApp `true` if the device's preferred dataset was contributed by this app.
         * @param updated `true`/`false` if the device credential was updated/removed to match the
         *   server, or `null` if no change was made.
         * @param exportIntent [IntentSender] to share the device dataset with the server, or `null`.
         */
        class AllHaveCredentials(
            val matches: Boolean?,
            val fromApp: Boolean?,
            val updated: Boolean?,
            val exportIntent: IntentSender?,
        ) : SyncResult()
    }

    /**
     * Predicted state of the Thread credentials stored on the device if a given credential were
     * added now.
     *
     * Surfaced to the user before a write so they understand the post-add state: Play Services
     * exposes no API to remove "preferred" status from a Thread credential once it has been
     * promoted, so a wrongly-promoted credential is hard to recover from inside the app.
     */
    sealed class PreflightOutcome {
        /** The credential being inspected is already this device's preferred Thread credential. */
        object AlreadyPreferred : PreflightOutcome()

        /**
         * Another credential the app added is already this device's preferred Thread network.
         * Play Services typically keeps preferring it; adding a new credential is unlikely to
         * change which network is preferred.
         */
        data class DifferentAppPreferred(val networkName: String) : PreflightOutcome()

        /**
         * The device has no preferred Thread credential we can detect. Adding a credential is
         * likely to make it the device-preferred one — used by Play Services when commissioning
         * new Matter+Thread devices.
         */
        object LikelyToBecomePreferred : PreflightOutcome()

        /**
         * The device has Thread credentials but our app has none preferred. Another app may
         * own the preferred credential; the outcome of adding ours is ambiguous.
         */
        object Unknown : PreflightOutcome()
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
     * @return a [SyncResult.ExportResult] — the variant subset that the export-only path can
     *   produce. Callers never have to handle [SyncResult.AllHaveCredentials].
     */
    suspend fun exportPreferredDataset(serverId: Int): SyncResult.ExportResult

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
     * Process the result from [exportPreferredDataset] or [getPreferredDatasetFromDevice]'s
     * intent and add the Thread dataset, if any, to the server.
     * @return Network name that was sent and accepted, or `null` if not sent or accepted
     */
    suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int): String?

    /**
     * Parse a Thread Active Operational Dataset TLV and return the embedded network name, or
     * `null` if the TLV is malformed or Thread isn't supported by the current build.
     */
    fun networkNameFromTlv(tlv: ByteArray): String?

    /**
     * Predict what state the Thread credentials stored on the device will be in if [tlv] is
     * added now.
     *
     * Used by the HA → Phone direction (the frontend Thread panel's "Send credentials to phone"
     * button) to surface a meaningful confirmation before mutating the stored credentials.
     */
    suspend fun predictPreferredOutcome(tlv: ByteArray): PreflightOutcome

    /**
     * Add the given Thread credential ([tlv]) to the device's credential storage under
     * [borderAgentId].
     *
     * The BA-ID is appended to the per-server tracked list so the orphan-cleanup path can
     * remove the credential when [serverId] is later removed. After the add the device's
     * preferred credential is queried so the caller can report the actual outcome.
     *
     * @return `true` if Play Services now reports the just-added credential as preferred,
     *   `false` if it prefers a different credential, or `null` if the post-add state could not
     *   be determined.
     */
    suspend fun addCredentialToDevice(serverId: Int, tlv: ByteArray, borderAgentId: String): Boolean?
}
