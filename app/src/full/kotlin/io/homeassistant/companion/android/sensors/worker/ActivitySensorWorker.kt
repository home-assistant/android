package io.homeassistant.companion.android.sensors.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.homeassistant.companion.android.sensors.ActivitySensorManager
import timber.log.Timber

/**
 * Processes activity recognition updates offloaded from
 * [io.homeassistant.companion.android.sensors.ActivitySensorBroadcastReceiver].
 *
 * Uses WorkManager to guarantee execution beyond the 10-second
 * BroadcastReceiver window, preventing silent update drops when the
 * process is killed.
 */
class ActivitySensorWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val activity = inputData.getString(KEY_ACTIVITY)
        if (activity == null) {
            Timber.w("Activity update missing activity string")
            return Result.failure()
        }

        val sensorManager = ActivitySensorManager()

        if (!sensorManager.isEnabled(applicationContext, ActivitySensorManager.activity)) {
            Timber.d("Activity sensor is disabled, skipping update")
            return Result.success()
        }

        Timber.d("Processing activity update: $activity")

        val confidenceKeys = inputData.getStringArray(KEY_CONFIDENCE_KEYS) ?: emptyArray()
        val confidenceValues = inputData.getIntArray(KEY_CONFIDENCE_VALUES) ?: intArrayOf()
        val confidences = confidenceKeys.zip(confidenceValues.toTypedArray()).toMap()
        val attributes: Map<String, Any?> = confidences +
            ("options" to ActivitySensorManager.ACTIVITY_OPTIONS)

        sensorManager.onSensorUpdated(
            applicationContext,
            ActivitySensorManager.activity,
            activity,
            ActivitySensorManager.getSensorIcon(activity),
            attributes,
        )

        return Result.success()
    }

    companion object {
        private const val KEY_ACTIVITY = "activity_type"
        private const val KEY_CONFIDENCE_KEYS = "confidence_keys"
        private const val KEY_CONFIDENCE_VALUES = "confidence_values"

        /**
         * Enqueues an activity recognition update for background processing.
         *
         * @param context application context
         * @param activity the most probable activity string (e.g. "walking", "still")
         * @param confidences map of activity type to confidence percentage
         */
        fun enqueue(
            context: Context,
            activity: String,
            confidences: Map<String, Int>,
        ) {
            val data = Data.Builder()
                .putString(KEY_ACTIVITY, activity)
                .putStringArray(KEY_CONFIDENCE_KEYS, confidences.keys.toTypedArray())
                .putIntArray(KEY_CONFIDENCE_VALUES, confidences.values.toIntArray())
                .build()

            val request = OneTimeWorkRequestBuilder<ActivitySensorWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
