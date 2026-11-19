package io.homeassistant.companion.android.thread

import android.app.Activity
import android.content.IntentSender
import android.os.Build
import androidx.activity.result.ActivityResult
import androidx.annotation.ChecksSdkIntAtLeast
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.threadnetwork.IsPreferredCredentialsResult
import com.google.android.gms.threadnetwork.ThreadBorderAgent
import com.google.android.gms.threadnetwork.ThreadNetworkClient
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.android.gms.threadnetwork.ThreadNetworkStatusCodes
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

class ThreadManagerImpl @Inject constructor(
    private val serverManager: ServerManager,
    @param:IsAutomotive private val isAutomotive: Boolean,
    private val threadNetworkClient: ThreadNetworkClient,
) : ThreadManager {
    companion object {
        // ID is a placeholder used in previous app versions / for older Home Assistant versions
        private const val BORDER_AGENT_ID = "0000000000000001"
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O_MR1)
    override fun appSupportsThread(): Boolean = SdkVersion.isAtLeast(Build.VERSION_CODES.O_MR1) &&
        !isAutomotive

    override suspend fun coreSupportsThread(serverId: Int): Boolean {
        if (!serverManager.isRegistered() || serverManager.getServer(serverId)?.user?.isAdmin != true) return false
        val config = serverManager.webSocketRepository(serverId).getConfig()
        return config != null &&
            config.components.contains("thread") &&
            HomeAssistantVersion.fromString(config.version)?.isAtLeast(2023, 3, 0) == true
    }

    private suspend fun getDatasetsFromServer(serverId: Int): List<ThreadDatasetResponse>? =
        serverManager.webSocketRepository(serverId).getThreadDatasets()

    override suspend fun exportPreferredDataset(serverId: Int): ThreadManager.SyncResult.ExportResult {
        if (!appSupportsThread()) return ThreadManager.SyncResult.AppUnsupported
        if (!coreSupportsThread(serverId)) return ThreadManager.SyncResult.ServerUnsupported

        val getDeviceDataset = try {
            getPreferredDatasetFromDevice()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            Timber.e(e, "Thread: export cannot be started")
            return if (e.statusCode == ThreadNetworkStatusCodes.LOCAL_NETWORK_NOT_CONNECTED) {
                ThreadManager.SyncResult.NotConnected
            } else {
                throw e
            }
        }

        if (getDeviceDataset == null) return ThreadManager.SyncResult.NoneHaveCredentials

        val appIsDevicePreferred = appOwnedPreferredCredential() != null
        Timber.d("Thread: device ${if (appIsDevicePreferred) "prefers" else "doesn't prefer"} dataset from app")
        return if (appIsDevicePreferred) {
            ThreadManager.SyncResult.OnlyOnServer(imported = false)
        } else {
            ThreadManager.SyncResult.OnlyOnDevice(exportIntent = getDeviceDataset)
        }
    }

    override suspend fun syncPreferredDataset(serverId: Int, scope: CoroutineScope): ThreadManager.SyncResult {
        if (!appSupportsThread()) return ThreadManager.SyncResult.AppUnsupported
        if (!coreSupportsThread(serverId)) return ThreadManager.SyncResult.ServerUnsupported
        return fullSyncPreferredDataset(serverId, scope)
    }

    private suspend fun fullSyncPreferredDataset(serverId: Int, scope: CoroutineScope): ThreadManager.SyncResult {
        deleteOrphanedThreadCredentials(serverId)

        val getDeviceDataset = scope.async { getPreferredDatasetFromDevice() }
        val getCoreDatasets = scope.async { getDatasetsFromServer(serverId) }
        val deviceThreadIntent = getDeviceDataset.await()
        val coreThreadDatasets = getCoreDatasets.await()
        val coreThreadDataset = coreThreadDatasets?.firstOrNull { it.preferred }

        return if (deviceThreadIntent == null && coreThreadDataset != null) {
            try {
                importDatasetFromServer(
                    coreThreadDataset.datasetId,
                    coreThreadDataset.preferredBorderAgentId,
                    serverId,
                )
                coreThreadDataset.preferredBorderAgentId?.let {
                    // The device may hold other, non-preferred credentials this app stored. Their
                    // IDs must stay tracked for the orphan cleanup to find them.
                    val repository = serverManager.integrationRepository(serverId)
                    repository.setThreadBorderAgentIds((repository.getThreadBorderAgentIds() + it).distinct())
                } // else added using placeholder, will be removed when core is updated
                Timber.d("Thread import to device completed")
                ThreadManager.SyncResult.OnlyOnServer(imported = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Thread import to device failed")
                ThreadManager.SyncResult.OnlyOnServer(imported = false)
            }
        } else if (deviceThreadIntent != null && coreThreadDataset == null) {
            Timber.d("Thread export is ready")
            ThreadManager.SyncResult.OnlyOnDevice(exportIntent = deviceThreadIntent)
        } else if (deviceThreadIntent != null && coreThreadDataset != null) {
            try {
                val coreIsDevicePreferred = isPreferredDatasetByDevice(coreThreadDataset.datasetId, serverId)
                Timber.d(
                    "Thread: device ${if (coreIsDevicePreferred) "prefers" else "doesn't prefer"} core preferred dataset",
                )
                val appIsDevicePreferred = coreIsDevicePreferred || appOwnedPreferredCredential() != null
                Timber.d(
                    "Thread: device ${if (appIsDevicePreferred) "prefers" else "doesn't prefer"} dataset from app",
                )

                var exportFromDevice = false
                var updated: Boolean? = null
                if (!coreIsDevicePreferred) {
                    if (appIsDevicePreferred) {
                        // Update or remove the device preferred credential to match core state.
                        // The device credential store currently doesn't allow the user to choose
                        // which credential should be used. To prevent unexpected behavior, HA only
                        // contributes one credential at a time, which is for _this_ server.
                        try {
                            val localIds = serverManager.servers().flatMap {
                                serverManager.integrationRepository(it.id).getThreadBorderAgentIds()
                            }
                            updated = if (coreThreadDataset.source != "Google") { // Credential from HA, update
                                val deleted = deleteThreadCredentials(
                                    localIds.filter { it != coreThreadDataset.preferredBorderAgentId },
                                )
                                importDatasetFromServer(
                                    coreThreadDataset.datasetId,
                                    coreThreadDataset.preferredBorderAgentId,
                                    serverId,
                                )
                                serverManager.servers().forEach {
                                    // Keep tracking IDs whose deletion failed so the orphan
                                    // cleanup can retry them later.
                                    val repository = serverManager.integrationRepository(it.id)
                                    val kept = repository.getThreadBorderAgentIds() - deleted
                                    repository.setThreadBorderAgentIds(
                                        if (it.id == serverId && coreThreadDataset.preferredBorderAgentId != null) {
                                            (kept + coreThreadDataset.preferredBorderAgentId!!).distinct()
                                        } else {
                                            kept
                                        },
                                    )
                                }
                                Timber.d(
                                    "Thread update device completed: deleted ${deleted.size} datasets, updated 1",
                                )
                                true
                            } else { // Core prefers imported from other app, this shouldn't be managed by HA
                                val deleted = deleteThreadCredentials(localIds)
                                serverManager.servers().forEach {
                                    // Keep tracking IDs whose deletion failed so the orphan
                                    // cleanup can retry them later.
                                    val repository = serverManager.integrationRepository(it.id)
                                    repository.setThreadBorderAgentIds(
                                        repository.getThreadBorderAgentIds() - deleted,
                                    )
                                }
                                Timber.d("Thread update device completed: deleted ${deleted.size} datasets")
                                false
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Thread update device failed")
                        }
                    } else {
                        exportFromDevice = true
                    }
                }

                // Import the dataset to core if different from device
                ThreadManager.SyncResult.AllHaveCredentials(
                    matches = coreIsDevicePreferred,
                    fromApp = appIsDevicePreferred,
                    updated = updated,
                    exportIntent = if (exportFromDevice) deviceThreadIntent else null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Thread device/core preferred comparison failed")
                ThreadManager.SyncResult.AllHaveCredentials(
                    matches = null,
                    fromApp = null,
                    updated = null,
                    exportIntent = null,
                )
            }
        } else {
            ThreadManager.SyncResult.NoneHaveCredentials
        }
    }

    override suspend fun getPreferredDatasetFromServer(serverId: Int): ThreadDatasetResponse? =
        getDatasetsFromServer(serverId)?.firstOrNull { it.preferred }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun importDatasetFromServer(datasetId: String, preferredBorderAgentId: String?, serverId: Int) {
        val tlv = serverManager.webSocketRepository(serverId).getThreadDatasetTlv(datasetId)?.tlvAsByteArray
        if (tlv != null) {
            val borderAgentId = preferredBorderAgentId ?: run {
                Timber.w("Adding dataset with placeholder border agent ID")
                BORDER_AGENT_ID
            }
            val idAsBytes = borderAgentId.let { if (it.length == 16) it.toByteArray() else it.hexToByteArray() }
            val threadBorderAgent = ThreadBorderAgent.newBuilder(idAsBytes).build()
            val threadNetworkCredentials = ThreadNetworkCredentials.fromActiveOperationalDataset(tlv)
            suspendCancellableCoroutine { cont ->
                threadNetworkClient
                    .addCredentials(threadBorderAgent, threadNetworkCredentials)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            }
        }
    }

    override suspend fun getPreferredDatasetFromDevice(): IntentSender? = suspendCancellableCoroutine { cont ->
        if (appSupportsThread()) {
            threadNetworkClient
                .preferredCredentials
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.intentSender) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        } else {
            cont.resumeWithException(IllegalStateException("Thread is not supported on SDK <27"))
        }
    }

    private suspend fun isPreferredDatasetByDevice(datasetId: String, serverId: Int): Boolean {
        val tlv = serverManager.webSocketRepository(serverId).getThreadDatasetTlv(datasetId)?.tlvAsByteArray
        return if (tlv != null) {
            val threadNetworkCredentials = ThreadNetworkCredentials.fromActiveOperationalDataset(tlv)
            isPreferredCredentials(threadNetworkCredentials)
        } else {
            false
        }
    }

    /** All Thread credentials this app has stored on the device, or `null` if they couldn't be read. */
    private suspend fun getAllCredentials(): List<ThreadNetworkCredentials>? = suspendCancellableCoroutine { cont ->
        threadNetworkClient
            .allCredentials
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener {
                Timber.w(it, "Thread: cannot read app credentials")
                if (cont.isActive) cont.resume(null)
            }
    }

    /**
     * The credential stored by this app that the device currently prefers, or `null` if the
     * device prefers none of them. A preferred credential owned by another app is not visible
     * to this app and also yields `null`.
     */
    private suspend fun appOwnedPreferredCredential(): ThreadNetworkCredentials? =
        preferredCredentialAmong(getAllCredentials())

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun preferredCredentialAmong(
        credentials: List<ThreadNetworkCredentials>?,
    ): ThreadNetworkCredentials? = try {
        credentials?.firstOrNull { isPreferredCredentials(it) }?.also {
            Timber.d(
                "Thread device prefers app added dataset: ${it.networkName} (PAN ${it.panId}, EXTPAN ${
                    it.extendedPanId.toHexString(HexFormat.UpperCase)
                })",
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Thread app added credentials preferred check failed")
        null
    }

    private suspend fun isPreferredCredentials(credentials: ThreadNetworkCredentials): Boolean =
        suspendCancellableCoroutine { cont ->
            threadNetworkClient
                .isPreferredCredentials(credentials)
                .addOnSuccessListener {
                    if (cont.isActive) {
                        cont.resume(
                            it == IsPreferredCredentialsResult.PREFERRED_CREDENTIALS_MATCHED,
                        )
                    }
                }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    override suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int): String? {
        if (result.resultCode == Activity.RESULT_OK && coreSupportsThread(serverId)) {
            val threadNetworkCredentials = ThreadNetworkCredentials.fromIntentSenderResultData(result.data!!)
            try {
                val added = serverManager.webSocketRepository(
                    serverId,
                ).addThreadDataset(threadNetworkCredentials.activeOperationalDataset)
                if (added) return threadNetworkCredentials.networkName
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error while executing server new Thread credentials request")
            }
        }
        return null
    }

    override fun networkNameFromTlv(tlv: ByteArray): String? = try {
        ThreadNetworkCredentials.fromActiveOperationalDataset(tlv).networkName
    } catch (e: Exception) {
        Timber.w(e, "Thread: cannot parse TLV to extract network name")
        null
    }

    override suspend fun predictPreferredOutcome(tlv: ByteArray): ThreadManager.PreflightOutcome {
        val prospective = try {
            ThreadNetworkCredentials.fromActiveOperationalDataset(tlv)
        } catch (e: Exception) {
            Timber.w(e, "Thread preflight: cannot parse TLV")
            null
        }
        val alreadyPreferred = prospective?.let {
            try {
                isPreferredCredentials(it)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Thread preflight: isPreferredCredentials failed")
                null
            }
        }
        return when {
            prospective == null || alreadyPreferred == null -> ThreadManager.PreflightOutcome.Unknown
            alreadyPreferred -> ThreadManager.PreflightOutcome.AlreadyPreferred
            else -> predictAmongAppCredentials()
        }
    }

    /**
     * Preflight continuation once the prospective credential is known not to be preferred:
     * classify the outcome from the app's own stored credentials. Distinguishes "nothing stored"
     * (this add will likely become preferred) from "we own credentials but none is preferred —
     * another app may own the preferred one" (ambiguous).
     */
    private suspend fun predictAmongAppCredentials(): ThreadManager.PreflightOutcome {
        val appCredentials = getAllCredentials()
        val appOwnedPreferred = preferredCredentialAmong(appCredentials)
        return when {
            appOwnedPreferred != null ->
                ThreadManager.PreflightOutcome.AppCredentialPreferred(appOwnedPreferred.networkName)
            appCredentials == null -> ThreadManager.PreflightOutcome.Unknown
            appCredentials.isEmpty() -> ThreadManager.PreflightOutcome.LikelyToBecomePreferred
            else -> ThreadManager.PreflightOutcome.Unknown
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun addCredentialToDevice(
        serverId: Int,
        tlv: ByteArray,
        borderAgentId: String,
    ): ThreadManager.AddCredentialResult {
        // Sweep stale credentials before adding.
        deleteOrphanedThreadCredentials(serverId)

        val threadBorderAgent = ThreadBorderAgent.newBuilder(borderAgentId.hexToByteArray()).build()
        val credentials = ThreadNetworkCredentials.fromActiveOperationalDataset(tlv)
        // Track the border agent ID before the write so the orphan path can clean it up if this
        // server is later removed. The device may still complete the write after this coroutine
        // is cancelled, so the ID is only dropped again when the write is known to have failed.
        val repository = serverManager.integrationRepository(serverId)
        val alreadyTracked = borderAgentId in repository.getThreadBorderAgentIds()
        if (!alreadyTracked) {
            repository.setThreadBorderAgentIds(repository.getThreadBorderAgentIds() + borderAgentId)
        }
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                threadNetworkClient
                    .addCredentials(threadBorderAgent, credentials)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!alreadyTracked) {
                repository.setThreadBorderAgentIds(repository.getThreadBorderAgentIds() - borderAgentId)
            }
            throw e
        }
        return preferredStateAfterAdd(credentials)
    }

    private suspend fun preferredStateAfterAdd(
        credentials: ThreadNetworkCredentials,
    ): ThreadManager.AddCredentialResult = try {
        if (isPreferredCredentials(credentials)) {
            ThreadManager.AddCredentialResult.Preferred
        } else {
            ThreadManager.AddCredentialResult.NotPreferred
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Thread: post-add preferred check failed")
        ThreadManager.AddCredentialResult.PreferredUnknown
    }

    private suspend fun deleteOrphanedThreadCredentials(serverId: Int) {
        if (serverManager.servers().all { it.version?.isAtLeast(2023, 9) == true }) {
            try {
                deleteThreadCredential(BORDER_AGENT_ID)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Expected, it may not exist
            }
        }

        val orphanedCredentials = serverManager.integrationRepository(serverId).getOrphanedThreadBorderAgentIds()
        if (orphanedCredentials.isEmpty()) return

        orphanedCredentials.forEach {
            try {
                deleteThreadCredential(it)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unable to delete credential for border agent ID $it")
            }
        }
        serverManager.integrationRepository(serverId).clearOrphanedThreadBorderAgentIds()
    }

    /**
     * Delete the credentials for the given border agent IDs from the device, logging failures.
     *
     * @return the IDs that were actually deleted; failed ones should stay tracked so the orphan
     *   cleanup can retry them later.
     */
    private suspend fun deleteThreadCredentials(borderAgentIds: List<String>): Set<String> {
        val deleted = mutableSetOf<String>()
        borderAgentIds.forEach { baId ->
            try {
                deleteThreadCredential(baId)
                deleted += baId
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to delete credential for border agent ID $baId")
            }
        }
        return deleted
    }

    private suspend fun deleteThreadCredential(borderAgentId: String) = suspendCancellableCoroutine { cont ->
        val idAsBytes = borderAgentId.let { if (it.length == 16) it.toByteArray() else it.hexToByteArray() }
        val threadBorderAgent = ThreadBorderAgent.newBuilder(idAsBytes).build()
        threadNetworkClient
            .removeCredentials(threadBorderAgent)
            .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }
}
