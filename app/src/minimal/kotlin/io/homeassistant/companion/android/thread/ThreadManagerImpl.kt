package io.homeassistant.companion.android.thread

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class ThreadManagerImpl @Inject constructor() : ThreadManager {

    // Thread support currently depends on Google Play Services,
    // and as a result Thread is not supported with the minimal flavor

    override fun appSupportsThread(): Boolean = false

    override suspend fun coreSupportsThread(serverId: Int): Boolean = false

    override suspend fun syncPreferredDataset(
        context: Context,
        serverId: Int,
        exportOnly: Boolean,
        scope: CoroutineScope,
    ): ThreadManager.SyncResult = ThreadManager.SyncResult.AppUnsupported

    override suspend fun getPreferredDatasetFromServer(serverId: Int): ThreadDatasetResponse? = null

    override suspend fun importDatasetFromServer(
        context: Context,
        datasetId: String,
        preferredBorderAgentId: String?,
        serverId: Int,
    ) { }

    override suspend fun getPreferredDatasetFromDevice(context: Context): IntentSender? {
        throw IllegalStateException("Thread is not supported with the minimal flavor")
    }

    override suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int): String? = null
}
