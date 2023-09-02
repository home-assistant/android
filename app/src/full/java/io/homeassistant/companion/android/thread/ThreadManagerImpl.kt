package io.homeassistant.companion.android.thread

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResult
import com.google.android.gms.threadnetwork.IsPreferredCredentialsResult
import com.google.android.gms.threadnetwork.ThreadBorderAgent
import com.google.android.gms.threadnetwork.ThreadNetwork
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ThreadManagerImpl @Inject constructor(
    private val serverManager: ServerManager,
    private val packageManager: PackageManager
) : ThreadManager {
    companion object {
        private const val TAG = "ThreadManagerImpl"

        // ID is a placeholder used in previous app versions / for older Home Assistant versions
        private const val BORDER_AGENT_ID = "0000000000000001"
    }

    override fun appSupportsThread(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    override suspend fun coreSupportsThread(serverId: Int): Boolean {
        if (!serverManager.isRegistered() || serverManager.getServer(serverId)?.user?.isAdmin != true) return false
        val config = serverManager.webSocketRepository(serverId).getConfig()
        return config != null &&
            config.components.contains("thread") &&
            HomeAssistantVersion.fromString(config.version)?.isAtLeast(2023, 3, 0) == true
    }

    private suspend fun getDatasetsFromServer(serverId: Int): List<ThreadDatasetResponse>? =
        serverManager.webSocketRepository(serverId).getThreadDatasets()

    override suspend fun syncPreferredDataset(
        context: Context,
        serverId: Int,
        scope: CoroutineScope
    ): ThreadManager.SyncResult {
        if (!appSupportsThread()) return ThreadManager.SyncResult.AppUnsupported
        if (!coreSupportsThread(serverId)) return ThreadManager.SyncResult.ServerUnsupported

        deleteOrphanedThreadCredentials(context, serverId)

        val getDeviceDataset = scope.async { getPreferredDatasetFromDevice(context) }
        val getCoreDatasets = scope.async { getDatasetsFromServer(serverId) }
        val deviceThreadIntent = getDeviceDataset.await()
        val coreThreadDatasets = getCoreDatasets.await()
        val coreThreadDataset = coreThreadDatasets?.firstOrNull { it.preferred }

        return if (deviceThreadIntent == null && coreThreadDataset != null) {
            try {
                importDatasetFromServer(context, coreThreadDataset.datasetId, coreThreadDataset.preferredBorderAgentId, serverId)
                serverManager.integrationRepository(serverId).setThreadBorderAgentIds(listOf((coreThreadDataset.preferredBorderAgentId ?: BORDER_AGENT_ID)))
                Log.d(TAG, "Thread import to device completed")
                ThreadManager.SyncResult.OnlyOnServer(imported = true)
            } catch (e: Exception) {
                Log.e(TAG, "Thread import to device failed", e)
                ThreadManager.SyncResult.OnlyOnServer(imported = false)
            }
        } else if (deviceThreadIntent != null && coreThreadDataset == null) {
            Log.d(TAG, "Thread export is ready")
            ThreadManager.SyncResult.OnlyOnDevice(exportIntent = deviceThreadIntent)
        } else if (deviceThreadIntent != null && coreThreadDataset != null) {
            try {
                val coreIsDevicePreferred = isPreferredDatasetByDevice(context, coreThreadDataset.datasetId, serverId)
                Log.d(TAG, "Thread: device ${if (coreIsDevicePreferred) "prefers" else "doesn't prefer" } core preferred dataset")
                val appIsDevicePreferred = coreIsDevicePreferred || appAddedIsPreferredCredentials(context)
                Log.d(TAG, "Thread: device ${if (appIsDevicePreferred) "prefers" else "doesn't prefer" } dataset from app")

                var exportFromDevice = false
                var updated: Boolean? = null
                if (!coreIsDevicePreferred) {
                    if (appIsDevicePreferred) {
                        // Update or remove the device preferred credential to match core state.
                        // The device credential store currently doesn't allow the user to choose
                        // which credential should be used. To prevent unexpected behavior, HA only
                        // contributes one credential at a time, which is for _this_ server.
                        try {
                            val localIds = serverManager.defaultServers.flatMap {
                                serverManager.integrationRepository(it.id).getThreadBorderAgentIds()
                            }.toMutableList()
                            if (localIds.isEmpty()) { // Prefers something from HA, must've been added before BA ID logic
                                localIds += BORDER_AGENT_ID
                            }

                            updated = if (coreThreadDataset.source != "Google") { // Credential from HA, update
                                localIds.filter { it != coreThreadDataset.preferredBorderAgentId }.forEach { baId ->
                                    try {
                                        deleteThreadCredential(context, baId)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Unable to delete credential for border agent ID $baId", e)
                                    }
                                }
                                importDatasetFromServer(context, coreThreadDataset.datasetId, coreThreadDataset.preferredBorderAgentId, serverId)
                                serverManager.defaultServers.forEach {
                                    serverManager.integrationRepository(it.id).setThreadBorderAgentIds(
                                        if (it.id == serverId) {
                                            listOf(coreThreadDataset.preferredBorderAgentId ?: BORDER_AGENT_ID)
                                        } else {
                                            emptyList()
                                        }
                                    )
                                }
                                true
                            } else { // Core prefers imported from other app, this shouldn't be managed by HA
                                localIds.forEach { baId ->
                                    try {
                                        deleteThreadCredential(context, baId)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Unable to delete credential for border agent ID $baId", e)
                                    }
                                }
                                serverManager.defaultServers.forEach {
                                    serverManager.integrationRepository(it.id).setThreadBorderAgentIds(emptyList())
                                }
                                false
                            }
                            Log.d(TAG, "Thread update device completed")
                        } catch (e: Exception) {
                            Log.e(TAG, "Thread update device failed", e)
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
                    exportIntent = if (exportFromDevice) deviceThreadIntent else null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Thread device/core preferred comparison failed", e)
                ThreadManager.SyncResult.AllHaveCredentials(matches = null, fromApp = null, updated = null, exportIntent = null)
            }
        } else {
            ThreadManager.SyncResult.NoneHaveCredentials
        }
    }

    override suspend fun getPreferredDatasetFromServer(serverId: Int): ThreadDatasetResponse? =
        getDatasetsFromServer(serverId)?.firstOrNull { it.preferred }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun importDatasetFromServer(
        context: Context,
        datasetId: String,
        preferredBorderAgentId: String?,
        serverId: Int
    ) {
        val tlv = serverManager.webSocketRepository(serverId).getThreadDatasetTlv(datasetId)?.tlvAsByteArray
        if (tlv != null) {
            val borderAgentId = preferredBorderAgentId ?: run {
                Log.w(TAG, "Adding dataset with placeholder border agent ID")
                BORDER_AGENT_ID
            }
            val idAsBytes = borderAgentId.let { if (it.length == 16) it.toByteArray() else it.hexToByteArray() }
            val threadBorderAgent = ThreadBorderAgent.newBuilder(idAsBytes).build()
            val threadNetworkCredentials = ThreadNetworkCredentials.fromActiveOperationalDataset(tlv)
            suspendCoroutine { cont ->
                ThreadNetwork.getClient(context).addCredentials(threadBorderAgent, threadNetworkCredentials)
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        }
    }

    override suspend fun getPreferredDatasetFromDevice(context: Context): IntentSender? = suspendCoroutine { cont ->
        if (appSupportsThread()) {
            ThreadNetwork.getClient(context)
                .preferredCredentials
                .addOnSuccessListener { cont.resume(it.intentSender) }
                .addOnFailureListener { cont.resumeWithException(it) }
        } else {
            cont.resumeWithException(IllegalStateException("Thread is not supported on SDK <27"))
        }
    }

    private suspend fun isPreferredDatasetByDevice(context: Context, datasetId: String, serverId: Int): Boolean {
        val tlv = serverManager.webSocketRepository(serverId).getThreadDatasetTlv(datasetId)?.tlvAsByteArray
        return if (tlv != null) {
            val threadNetworkCredentials = ThreadNetworkCredentials.fromActiveOperationalDataset(tlv)
            isPreferredCredentials(context, threadNetworkCredentials)
        } else {
            false
        }
    }

    private suspend fun appAddedIsPreferredCredentials(context: Context): Boolean {
        val appCredentials = suspendCoroutine { cont ->
            ThreadNetwork.getClient(context)
                .allCredentials
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
        return try {
            appCredentials?.any { isPreferredCredentials(context, it) } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Thread app added credentials preferred check failed", e)
            false
        }
    }

    private suspend fun isPreferredCredentials(context: Context, credentials: ThreadNetworkCredentials): Boolean = suspendCoroutine { cont ->
        ThreadNetwork.getClient(context)
            .isPreferredCredentials(credentials)
            .addOnSuccessListener { cont.resume(it == IsPreferredCredentialsResult.PREFERRED_CREDENTIALS_MATCHED) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    override suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int): String? {
        if (result.resultCode == Activity.RESULT_OK && coreSupportsThread(serverId)) {
            val threadNetworkCredentials = ThreadNetworkCredentials.fromIntentSenderResultData(result.data!!)
            try {
                val added = serverManager.webSocketRepository(serverId).addThreadDataset(threadNetworkCredentials.activeOperationalDataset)
                if (added) return threadNetworkCredentials.networkName
            } catch (e: Exception) {
                Log.e(TAG, "Error while executing server new Thread credentials request", e)
            }
        }
        return null
    }

    private suspend fun deleteOrphanedThreadCredentials(context: Context, serverId: Int) {
        val orphanedCredentials = serverManager.integrationRepository(serverId).getThreadBorderAgentIds()
        if (orphanedCredentials.isEmpty()) return

        orphanedCredentials.forEach {
            try {
                deleteThreadCredential(context, it)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to delete credential for border agent ID $it", e)
            }
        }
        serverManager.integrationRepository(serverId).clearOrphanedThreadBorderAgentIds()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun deleteThreadCredential(context: Context, borderAgentId: String) = suspendCoroutine { cont ->
        val idAsBytes = borderAgentId.let { if (it.length == 16) it.toByteArray() else it.hexToByteArray() }
        val threadBorderAgent = ThreadBorderAgent.newBuilder(idAsBytes).build()
        ThreadNetwork.getClient(context)
            .removeCredentials(threadBorderAgent)
            .addOnSuccessListener { cont.resume(true) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
