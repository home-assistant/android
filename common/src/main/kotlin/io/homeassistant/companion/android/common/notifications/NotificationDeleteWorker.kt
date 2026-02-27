package io.homeassistant.companion.android.common.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.notification.NotificationDao
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Worker that fires the "mobile_app_notification_cleared" event to the Home Assistant server.
 */
internal class NotificationDeleteWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context.applicationContext, params) {

    companion object {
        private const val KEY_DATABASE_ID = "database_id"
        private const val KEY_EVENT_DATA_KEYS = "event_data_keys"
        private const val KEY_EVENT_DATA_VALUES = "event_data_values"

        /**
         * A bug in the AndroidX Hilt compiler that caused a StackOverflow in our codebase
         * tracked in https://github.com/google/dagger/issues/4702 forces us to use an entry point.
         */
        @EntryPoint
        @InstallIn(SingletonComponent::class)
        internal interface NotificationDeleteWorkerEntryPoint {
            fun serverManager(): ServerManager
            fun notificationDao(): NotificationDao
        }

        /**
         * Enqueues work to fire the notification delete event to the Home Assistant server.
         *
         * @param context The context to use for obtaining [WorkManager].
         * @param databaseId The database ID of the notification that was cleared.
         * @param eventDataKeys The keys of the event data to send to the server.
         * @param eventDataValues The values of the event data to send to the server, matching [eventDataKeys] by index.
         */
        internal fun enqueue(
            context: Context,
            databaseId: Long,
            eventDataKeys: Array<String?>,
            eventDataValues: Array<String?>,
        ) {
            val data = Data.Builder()
                .putLong(KEY_DATABASE_ID, databaseId)
                .putStringArray(KEY_EVENT_DATA_KEYS, eventDataKeys)
                .putStringArray(KEY_EVENT_DATA_VALUES, eventDataValues)
                .build()

            val request = OneTimeWorkRequestBuilder<NotificationDeleteWorker>()
                .setInputData(data)
                // We want the event to be sent right away if it is possible
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val databaseId = inputData.getLong(KEY_DATABASE_ID, 0)
        val keys = inputData.getStringArray(KEY_EVENT_DATA_KEYS) ?: return Result.failure()
        val values = inputData.getStringArray(KEY_EVENT_DATA_VALUES) ?: return Result.failure()

        val entryPoints = EntryPoints.get(applicationContext, NotificationDeleteWorkerEntryPoint::class.java)
        val serverManager = entryPoints.serverManager()
        val notificationDao = entryPoints.notificationDao()

        return try {
            val eventData = keys.zip(values).toMap()
            val serverId = notificationDao.get(databaseId.toInt())?.serverId ?: ServerManager.SERVER_ID_ACTIVE
            serverManager.integrationRepository(serverId).fireEvent("mobile_app_notification_cleared", eventData)
            Timber.d("Notification cleared event successful")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Issue sending notification cleared event to Home Assistant")
            Result.failure()
        }
    }
}
