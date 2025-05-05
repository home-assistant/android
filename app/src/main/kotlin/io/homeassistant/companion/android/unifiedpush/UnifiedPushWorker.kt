package io.homeassistant.companion.android.unifiedpush

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UnifiedPushWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "UnifiedPushWorker"

        fun start(context: Context, constraints: Constraints = Constraints.NONE) {
            val request = OneTimeWorkRequestBuilder<UnifiedPushWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        UnifiedPushManager.register(this@UnifiedPushWorker.applicationContext)
        Result.success()
    }
}
