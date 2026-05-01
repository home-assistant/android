package io.homeassistant.companion.android.sensors.healthconnect

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.homeassistant.companion.android.sensors.SensorReceiver
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Periodic WorkManager job that polls the Health Connect Changes API for deltas the
 * 15-minute [io.homeassistant.companion.android.sensors.SensorWorker] would otherwise wait
 * for. Triggers a sensor refresh whenever any subscribed data type observes a change.
 *
 * Opt-in: [start] is a no-op unless the user has flipped on
 * [HealthConnectSyncPreferences.isRealtimeSyncEnabled], so devices that don't want the extra
 * polling cadence pay nothing. The worker self-checks the flag on each run as well — that
 * way, a flag flip racing with a fired worker resolves to "skip this cycle" rather than
 * surprising the user with one extra poll.
 *
 * Cadence: 15 minutes is the lower bound WorkManager enforces for periodic jobs, so the
 * "every 5 min" promised by the plan is delivered via flex intervals — WorkManager runs
 * the worker at some point inside the last [FLEX_INTERVAL_MIN] minutes of each
 * [REPEAT_INTERVAL_MIN]-minute window. In practice this gives ≤5-min latency for catching
 * a third-party HC write, which is the user-visible goal.
 */
class HealthConnectChangesWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun changesRepository(): HealthConnectChangesRepository
        fun preferences(): HealthConnectSyncPreferences
    }

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, Entry::class.java)
        if (!entry.preferences().isRealtimeSyncEnabled()) {
            Timber.d("Health Connect real-time sync disabled — skipping changes poll")
            return Result.success()
        }
        val changed = entry.changesRepository().pollChanges(HealthConnectDataType.all)
        return when {
            changed == null -> {
                // Health Connect unavailable on this device. Nothing to do; don't retry.
                Result.success()
            }
            changed.isEmpty() -> Result.success()
            else -> {
                Timber.d("Health Connect changes detected for: ${changed.joinToString { it.key }}")
                SensorReceiver.updateAllSensors(applicationContext)
                Result.success()
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "HealthConnectChangesWorker"
        const val REPEAT_INTERVAL_MIN = 15L
        const val FLEX_INTERVAL_MIN = 5L

        fun start(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthConnectChangesWorker>(
                REPEAT_INTERVAL_MIN,
                TimeUnit.MINUTES,
                FLEX_INTERVAL_MIN,
                TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
