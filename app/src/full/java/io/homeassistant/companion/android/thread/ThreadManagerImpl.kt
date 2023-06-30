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

        // ID is a placeholder while we wait for Google to remove the requirement to provide one
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

        val getDeviceDataset = scope.async { getPreferredDatasetFromDevice(context) }
        val getCoreDatasets = scope.async { getDatasetsFromServer(serverId) }
        val deviceThreadIntent = getDeviceDataset.await()
        val coreThreadDatasets = getCoreDatasets.await()
        val coreThreadDataset = coreThreadDatasets?.firstOrNull { it.preferred }

        return if (deviceThreadIntent == null && coreThreadDataset != null) {
            try {
                importDatasetFromServer(context, coreThreadDataset.datasetId, serverId)
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
                // Import the dataset to core if different from device
                ThreadManager.SyncResult.AllHaveCredentials(
                    matches = coreIsDevicePreferred,
                    exportIntent = if (coreIsDevicePreferred) null else deviceThreadIntent
                )
            } catch (e: Exception) {
                Log.e(TAG, "Thread device/core preferred comparison failed", e)
                ThreadManager.SyncResult.AllHaveCredentials(matches = null, exportIntent = null)
            }
        } else {
            ThreadManager.SyncResult.NoneHaveCredentials
        }
    }

    override suspend fun getPreferredDatasetFromServer(serverId: Int): ThreadDatasetResponse? =
        getDatasetsFromServer(serverId)?.firstOrNull { it.preferred }

    override suspend fun importDatasetFromServer(context: Context, datasetId: String, serverId: Int) {
        val tlv = serverManager.webSocketRepository(serverId).getThreadDatasetTlv(datasetId)?.tlvAsByteArray
        if (tlv != null) {
            val threadBorderAgent = ThreadBorderAgent.newBuilder(BORDER_AGENT_ID.toByteArray()).build()
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
        if (tlv != null) {
            val threadNetworkCredentials = ThreadNetworkCredentials.fromActiveOperationalDataset(tlv)
            return suspendCoroutine { cont ->
                ThreadNetwork.getClient(context)
                    .isPreferredCredentials(threadNetworkCredentials)
                    .addOnSuccessListener { cont.resume(it == IsPreferredCredentialsResult.PREFERRED_CREDENTIALS_MATCHED) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        }
        return false
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
}
