package io.homeassistant.companion.android.thread

import android.content.Context
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import javax.inject.Inject

class ThreadManagerImpl @Inject constructor() : ThreadManager {

    // Thread support currently depends on Google Play Services,
    // and as a result Thread is not supported with the minimal flavor

    override fun appSupportsThread(): Boolean = false

    override suspend fun coreSupportsThread(serverId: Int): Boolean = false

    override suspend fun getPreferredDatasetFromServer(serverId: Int): ThreadDatasetResponse? = null

    override suspend fun importPreferredDatasetFromServer(context: Context, serverId: Int) { }

    override suspend fun getThreadPreferredDatasetExport(context: Context): IntentSender? {
        throw IllegalStateException("Thread is not supported with the minimal flavor")
    }

    override suspend fun sendThreadDatasetExportResult(result: ActivityResult, serverId: Int) { }
}
