package io.homeassistant.companion.android.sensors.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.homeassistant.companion.android.sensors.ActivitySensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import timber.log.Timber

/**
 * Processes sleep sensor updates (classify and segment events) offloaded
 * from [io.homeassistant.companion.android.sensors.ActivitySensorBroadcastReceiver].
 *
 * Uses WorkManager to guarantee execution beyond the 10-second
 * BroadcastReceiver window, preventing silent update drops when the
 * process is killed.
 */
class SleepSensorWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    /** Sleep classify event data extracted from a GMS intent */
    data class ClassifyData(
        val confidence: Int,
        val light: Int,
        val motion: Int,
        val timestampMillis: Long,
    )

    /** Sleep segment event data extracted from a GMS intent */
    data class SegmentData(
        val durationMillis: Long,
        val startMillis: Long,
        val endMillis: Long,
        val status: String,
    )

    override suspend fun doWork(): Result {
        Timber.d("Processing sleep update")
        val sensorManager = ActivitySensorManager()

        val classifyData = if (inputData.keyValueMap.containsKey(KEY_CLASSIFY_CONFIDENCE)) {
            ClassifyData(
                confidence = inputData.getInt(KEY_CLASSIFY_CONFIDENCE, -1),
                light = inputData.getInt(KEY_CLASSIFY_LIGHT, 0),
                motion = inputData.getInt(KEY_CLASSIFY_MOTION, 0),
                timestampMillis = inputData.getLong(KEY_CLASSIFY_TIMESTAMP, 0L),
            )
        } else {
            null
        }

        if (classifyData != null && sensorManager.isEnabled(applicationContext, ActivitySensorManager.sleepConfidence)) {
            Timber.d("Updating sleep confidence sensor")
            sensorManager.onSensorUpdated(
                applicationContext,
                ActivitySensorManager.sleepConfidence,
                classifyData.confidence,
                ActivitySensorManager.sleepConfidence.statelessIcon,
                mapOf(
                    "light" to classifyData.light,
                    "motion" to classifyData.motion,
                    "timestamp" to classifyData.timestampMillis,
                ),
            )
            SensorReceiver.updateAllSensors(applicationContext)
        }

        val segmentData = if (inputData.keyValueMap.containsKey(KEY_SEGMENT_DURATION)) {
            SegmentData(
                durationMillis = inputData.getLong(KEY_SEGMENT_DURATION, -1L),
                startMillis = inputData.getLong(KEY_SEGMENT_START, 0L),
                endMillis = inputData.getLong(KEY_SEGMENT_END, 0L),
                status = inputData.getString(KEY_SEGMENT_STATUS) ?: "unknown",
            )
        } else {
            null
        }

        if (segmentData != null && sensorManager.isEnabled(applicationContext, ActivitySensorManager.sleepSegment)) {
            Timber.d("Updating sleep segment sensor")
            sensorManager.onSensorUpdated(
                applicationContext,
                ActivitySensorManager.sleepSegment,
                segmentData.durationMillis,
                ActivitySensorManager.sleepSegment.statelessIcon,
                mapOf(
                    "start" to segmentData.startMillis,
                    "end" to segmentData.endMillis,
                    "status" to segmentData.status,
                ),
            )
        }

        return Result.success()
    }

    companion object {
        private const val KEY_CLASSIFY_CONFIDENCE = "classify_confidence"
        private const val KEY_CLASSIFY_LIGHT = "classify_light"
        private const val KEY_CLASSIFY_MOTION = "classify_motion"
        private const val KEY_CLASSIFY_TIMESTAMP = "classify_timestamp"
        private const val KEY_SEGMENT_DURATION = "segment_duration"
        private const val KEY_SEGMENT_START = "segment_start"
        private const val KEY_SEGMENT_END = "segment_end"
        private const val KEY_SEGMENT_STATUS = "segment_status"

        /**
         * Enqueues a sleep sensor update for background processing.
         *
         * @param context application context
         * @param classifyData sleep classify event data, or null if no classify event
         * @param segmentData sleep segment event data, or null if no segment event
         */
        fun enqueue(
            context: Context,
            classifyData: ClassifyData?,
            segmentData: SegmentData?,
        ) {
            val builder = Data.Builder()

            if (classifyData != null) {
                builder.putInt(KEY_CLASSIFY_CONFIDENCE, classifyData.confidence)
                builder.putInt(KEY_CLASSIFY_LIGHT, classifyData.light)
                builder.putInt(KEY_CLASSIFY_MOTION, classifyData.motion)
                builder.putLong(KEY_CLASSIFY_TIMESTAMP, classifyData.timestampMillis)
            }

            if (segmentData != null) {
                builder.putLong(KEY_SEGMENT_DURATION, segmentData.durationMillis)
                builder.putLong(KEY_SEGMENT_START, segmentData.startMillis)
                builder.putLong(KEY_SEGMENT_END, segmentData.endMillis)
                builder.putString(KEY_SEGMENT_STATUS, segmentData.status)
            }

            val request = OneTimeWorkRequestBuilder<SleepSensorWorker>()
                .setInputData(builder.build())
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
