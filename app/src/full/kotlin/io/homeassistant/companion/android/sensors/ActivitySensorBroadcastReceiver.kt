package io.homeassistant.companion.android.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.sensors.worker.ActivitySensorWorker
import io.homeassistant.companion.android.sensors.worker.SleepSensorWorker
import timber.log.Timber

/**
 * Lightweight receiver for GMS activity recognition and sleep intents.
 *
 * Extracts data synchronously from the intent and enqueues an
 * [ActivitySensorWorker] or [SleepSensorWorker] for the actual sensor
 * update work, to keep the BroadcastReceiver lifecycle bellow 10s.
 */
class ActivitySensorBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ActivitySensorManager.ACTION_UPDATE_ACTIVITY -> handleActivityIntent(context, intent)
            ActivitySensorManager.ACTION_SLEEP_ACTIVITY -> handleSleepIntent(context, intent)
            else -> Timber.w("Unknown intent action: ${intent.action}")
        }
    }

    private fun handleActivityIntent(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) {
            Timber.w("Activity intent has no recognition result")
            return
        }
        val result = ActivityRecognitionResult.extractResult(intent) ?: return

        var probActivity = typeToString(result.mostProbableActivity)
        if (probActivity == "on_foot") {
            probActivity = getSubActivity(result)
        }

        val confidences = result.probableActivities.associate { typeToString(it) to it.confidence }

        ActivitySensorWorker.enqueue(
            context = context,
            activity = probActivity,
            confidences = confidences,
        )
    }

    private fun handleSleepIntent(context: Context, intent: Intent) {
        val hasClassifyEvents = SleepClassifyEvent.hasEvents(intent)
        val hasSegmentEvents = SleepSegmentEvent.hasEvents(intent)

        if (!hasClassifyEvents && !hasSegmentEvents) {
            Timber.w("Sleep intent has no classify or segment events")
            return
        }

        val classifyData = if (hasClassifyEvents) {
            val events = SleepClassifyEvent.extractEvents(intent)
            events.lastOrNull()?.let {
                SleepSensorWorker.ClassifyData(
                    confidence = it.confidence,
                    light = it.light,
                    motion = it.motion,
                    timestampMillis = it.timestampMillis,
                )
            }
        } else {
            null
        }

        val segmentData = if (hasSegmentEvents) {
            val events = SleepSegmentEvent.extractEvents(intent)
            events.lastOrNull()?.let {
                SleepSensorWorker.SegmentData(
                    durationMillis = it.segmentDurationMillis,
                    startMillis = it.startTimeMillis,
                    endMillis = it.endTimeMillis,
                    status = getSleepSegmentStatus(it.status),
                )
            }
        } else {
            null
        }

        SleepSensorWorker.enqueue(
            context = context,
            classifyData = classifyData,
            segmentData = segmentData,
        )
    }

    private fun typeToString(activity: DetectedActivity): String {
        return when (activity.type) {
            DetectedActivity.IN_VEHICLE -> "in_vehicle"
            DetectedActivity.ON_BICYCLE -> "on_bicycle"
            DetectedActivity.ON_FOOT -> "on_foot"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.STILL -> "still"
            DetectedActivity.TILTING -> "tilting"
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }
    }

    private fun getSubActivity(result: ActivityRecognitionResult): String {
        if (result.probableActivities[1].type == DetectedActivity.RUNNING) return "running"
        if (result.probableActivities[1].type == DetectedActivity.WALKING) return "walking"
        return "on_foot"
    }

    private fun getSleepSegmentStatus(status: Int): String {
        return when (status) {
            SleepSegmentEvent.STATUS_SUCCESSFUL -> "successful"
            SleepSegmentEvent.STATUS_MISSING_DATA -> "missing data"
            SleepSegmentEvent.STATUS_NOT_DETECTED -> "not detected"
            else -> STATE_UNKNOWN
        }
    }
}
