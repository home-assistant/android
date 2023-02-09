package io.homeassistant.companion.android.google.sensors

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import com.google.android.gms.location.SleepSegmentRequest
import com.google.android.gms.tasks.Task
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import java.util.concurrent.TimeUnit

object ActivityIntentData {
    const val ACTION_UPDATE_ACTIVITY =
        "io.homeassistant.companion.android.background.UPDATE_ACTIVITY"

    const val ACTION_SLEEP_ACTIVITY =
        "io.homeassistant.companion.android.background.SLEEP_ACTIVITY"
}

const val TAG = "ActivitySM"
fun getActivityPendingIntent(context: Context, manager: Class<*>): PendingIntent {
    val intent = Intent(context, manager)
    intent.action = ActivityIntentData.ACTION_UPDATE_ACTIVITY
    return PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
}

fun getSleepPendingIntent(context: Context, manager: Class<*>): PendingIntent {
    val intent = Intent(context, manager)
    intent.action = ActivityIntentData.ACTION_SLEEP_ACTIVITY
    return PendingIntent.getBroadcast(
        context,
        1,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
}

fun getProbActivity(intent: Intent): String? {
    return if (ActivityRecognitionResult.hasResult(intent)) {
        val result = ActivityRecognitionResult.extractResult(intent)
        var probActivity = result?.let { typeToString(it.mostProbableActivity) }

        if (probActivity == "on_foot")
            probActivity = result?.let { getSubActivity(it) }

        probActivity
    } else
        null
}

fun getActivityAttributes(intent: Intent): Map<String, Any?>? {
    val result = ActivityRecognitionResult.extractResult(intent)
    return result?.probableActivities?.associate { typeToString(it) to it.confidence }
}

fun hasSleepClassifyEvents(intent: Intent): Boolean {
    return SleepClassifyEvent.hasEvents(intent)
}

fun hasSleepSegmentEvent(intent: Intent): Boolean {
    return SleepSegmentEvent.hasEvents(intent)
}

fun getSleepClassifyEvent(intent: Intent): Map<String, Any>? {
    val sleepClassifyEvent = SleepClassifyEvent.extractEvents(intent)
    if (sleepClassifyEvent.isEmpty())
        return null

    return mapOf(
        "confidence" to sleepClassifyEvent.last().confidence,
        "light" to sleepClassifyEvent.last().light,
        "motion" to sleepClassifyEvent.last().motion,
        "timestamp" to sleepClassifyEvent.last().timestampMillis
    )
}

fun getSleepSegmentEvent(intent: Intent): Map<String, Any>? {
    val sleepSegmentEvent = SleepSegmentEvent.extractEvents(intent)
    if (sleepSegmentEvent.isEmpty())
        return null

    return mapOf(
        "duration" to sleepSegmentEvent.last().segmentDurationMillis,
        "start" to sleepSegmentEvent.last().startTimeMillis,
        "end" to sleepSegmentEvent.last().endTimeMillis,
        "status" to sleepSegmentEvent.last().status
    )
}

fun typeToString(activity: DetectedActivity): String {
    return when (activity.type) {
        DetectedActivity.IN_VEHICLE -> "in_vehicle"
        DetectedActivity.ON_BICYCLE -> "on_bicycle"
        DetectedActivity.ON_FOOT -> "on_foot"
        DetectedActivity.RUNNING -> "running"
        DetectedActivity.STILL -> "still"
        DetectedActivity.TILTING -> "tilting"
        DetectedActivity.WALKING -> "walking"
        DetectedActivity.UNKNOWN -> "unknown"
        else -> "unknown"
    }
}

fun getSubActivity(result: ActivityRecognitionResult): String {
    if (result.probableActivities[1].type == DetectedActivity.RUNNING) return "running"
    if (result.probableActivities[1].type == DetectedActivity.WALKING) return "walking"
    return "on_foot"
}

fun getSleepSegmentStatus(int: Int): String {
    return when (int) {
        SleepSegmentEvent.STATUS_SUCCESSFUL -> "successful"
        SleepSegmentEvent.STATUS_MISSING_DATA -> "missing data"
        SleepSegmentEvent.STATUS_NOT_DETECTED -> "not detected"
        else -> "unknown"
    }
}

fun enableActivityUpdates(context: Context, activity: Class<*>, enabled: Boolean) {
    val actReg = ActivityRecognition.getClient(context)
    val pendingIntent = getActivityPendingIntent(context, activity)
    Log.d(TAG, "Unregistering for activity updates.")
    actReg.removeActivityUpdates(pendingIntent)

    if (!enabled)
        return
    Log.d(TAG, "Registering for activity updates.")
    val fastUpdate = SensorReceiverBase.shouldDoFastUpdates(context)
    actReg.requestActivityUpdates(TimeUnit.MINUTES.toMillis(if (fastUpdate) 1 else 2), pendingIntent)
}

fun enableSleepUpdates(context: Context, type: String, pendingIntent: PendingIntent): Task<Void> {
    return when (type) {
        "classify_only" -> {
            Log.d(TAG, "Registering for sleep confidence updates only")
            ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                pendingIntent,
                SleepSegmentRequest(SleepSegmentRequest.CLASSIFY_EVENTS_ONLY)
            )
        }
        "events_only" -> {
            Log.d(TAG, "Registering for sleep segment updates only")
            ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                pendingIntent,
                SleepSegmentRequest(SleepSegmentRequest.SEGMENT_EVENTS_ONLY)
            )
        }
        else -> {
            Log.d(TAG, "Registering for both sleep confidence and segment updates")
            ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                pendingIntent,
                SleepSegmentRequest.getDefaultSleepSegmentRequest()
            )
        }
    }
}

fun removeSleepUpdates(context: Context, pendingIntent: PendingIntent) {
    ActivityRecognition.getClient(context).removeSleepSegmentUpdates(pendingIntent)
}

fun getSensorIcon(activity: String): String {

    return when (activity) {
        "in_vehicle" -> "mdi:car"
        "on_bicycle" -> "mdi:bike"
        "on_foot" -> "mdi:shoe-print"
        "still" -> "mdi:sleep"
        "tilting" -> "mdi:phone-rotate-portrait"
        "walking" -> "mdi:walk"
        "running" -> "mdi:run"
        else -> "mdi:progress-question"
    }
}
