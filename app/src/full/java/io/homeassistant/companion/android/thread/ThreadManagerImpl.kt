package io.homeassistant.companion.android.thread

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResult
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
    private val serverManager: ServerManager
) : ThreadManager {
    companion object {
        private const val TAG = "ThreadManagerImpl"

        // ID is a placeholder while we wait for Google to remove the requirement to provide one
        private const val BORDER_AGENT_ID = "0000000000000001"
    }

    override fun appSupportsThread(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

    override suspend fun coreSupportsThread(serverId: Int): Boolean {
        if (!serverManager.isRegistered() || serverManager.getServer(serverId) == null) return false
        val config = serverManager.webSocketRepository(serverId).getConfig()
        return config != null &&
            config.components.contains("thread") &&
            HomeAssistantVersion.fromString(config.version)?.isAtLeast(2023, 3, 0) == true
    }

    override suspend fun syncPreferredDataset(
        context: Context,
        serverId: Int,
        scope: CoroutineScope
    ): IntentSender? {
        if (!appSupportsThread() || !coreSupportsThread(serverId)) return null

        val getDeviceDataset = scope.async { getPreferredDatasetFromDevice(context) }
        val getCoreDataset = scope.async { getPreferredDatasetFromServer(serverId) }
        val deviceThreadIntent = getDeviceDataset.await()
        val coreThreadDataset = getCoreDataset.await()

        if (deviceThreadIntent == null && coreThreadDataset != null) {
            try {
                importDatasetFromServer(context, coreThreadDataset.datasetId, serverId)
                Log.d(TAG, "Thread import to device completed")
            } catch (e: Exception) {
                Log.e(TAG, "Thread import to device failed", e)
            }
        } else if (deviceThreadIntent != null && coreThreadDataset == null) {
            Log.d(TAG, "Thread export is ready")
            return deviceThreadIntent
        } // else if device and core both have or don't have datasets, continue

        return null
    }

    override suspend fun getPreferredDatasetFromServer(serverId: Int): ThreadDatasetResponse? {
        val datasets = serverManager.webSocketRepository(serverId).getThreadDatasets()
        return datasets?.firstOrNull { it.preferred }
    }

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

    override suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int) {
        if (result.resultCode == Activity.RESULT_OK && coreSupportsThread(serverId)) {
            val threadNetworkCredentials = ThreadNetworkCredentials.fromIntentSenderResultData(result.data!!)
            try {
                serverManager.webSocketRepository(serverId).addThreadDataset(threadNetworkCredentials.activeOperationalDataset)
            } catch (e: Exception) {
                Log.e(TAG, "Error while executing server new Thread credentials request", e)
            }
        }
    }
}
