package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import com.google.android.gms.location.SleepSegmentRequest
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import java.util.concurrent.TimeUnit
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ActivitySensorManager : BroadcastReceiver(), SensorManager {

    companion object {

        internal const val TAG = "ActivitySM"

        const val ACTION_UPDATE_ACTIVITY =
            "io.homeassistant.companion.android.background.UPDATE_ACTIVITY"

        const val ACTION_SLEEP_ACTIVITY =
            "io.homeassistant.companion.android.background.SLEEP_ACTIVITY"
        private var sleepRegistration = false

        private val activity = SensorManager.BasicSensor(
            "detected_activity",
            "sensor",
            commonR.string.basic_sensor_name_activity,
            commonR.string.sensor_description_detected_activity,
            "mdi:walk"
        )

        private val sleepConfidence = SensorManager.BasicSensor(
            "sleep_confidence",
            "sensor",
            commonR.string.basic_sensor_name_sleep_confidence,
            commonR.string.sensor_description_sleep_confidence,
            "mdi:sleep",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            updateType = SensorManager.BasicSensor.UpdateType.CUSTOM
        )

        private val sleepSegment = SensorManager.BasicSensor(
            "sleep_segment",
            "sensor",
            commonR.string.basic_sensor_name_sleep_segment,
            commonR.string.sensor_description_sleep_segment,
            "mdi:sleep",
            unitOfMeasurement = "ms",
            updateType = SensorManager.BasicSensor.UpdateType.CUSTOM
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_UPDATE_ACTIVITY -> handleActivityUpdate(intent, context)
            ACTION_SLEEP_ACTIVITY -> handleSleepUpdate(intent, context)
            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun getActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivitySensorManager::class.java)
        intent.action = ACTION_UPDATE_ACTIVITY
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun getSleepPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivitySensorManager::class.java)
        intent.action = ACTION_SLEEP_ACTIVITY
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun handleActivityUpdate(intent: Intent, context: Context) {
        Log.d(TAG, "Received activity update.")
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            var probActivity = result?.let { typeToString(it.mostProbableActivity) }

            if (probActivity == "on_foot") {
                probActivity = result?.let { getSubActivity(it) }
            }

            if (probActivity != null && result != null) {
                onSensorUpdated(
                    context,
                    activity,
                    probActivity,
                    getSensorIcon(probActivity),
                    result.probableActivities.associate { typeToString(it) to it.confidence }
                )
            }
        }
    }

    private fun handleSleepUpdate(intent: Intent, context: Context) {
        Log.d(TAG, "Received sleep update")
        if (SleepClassifyEvent.hasEvents(intent) && isEnabled(context, sleepConfidence)) {
            Log.d(TAG, "Sleep classify event detected")
            val sleepClassifyEvent = SleepClassifyEvent.extractEvents(intent)
            if (sleepClassifyEvent.size > 0) {
                Log.d(TAG, "Sleep classify has an actual event")
                onSensorUpdated(
                    context,
                    sleepConfidence,
                    sleepClassifyEvent.last().confidence,
                    sleepConfidence.statelessIcon,
                    mapOf(
                        "light" to sleepClassifyEvent.last().light,
                        "motion" to sleepClassifyEvent.last().motion,
                        "timestamp" to sleepClassifyEvent.last().timestampMillis
                    )
                )

                // Send the update immediately
                SensorReceiver.updateAllSensors(context)
            }
        }
        if (SleepSegmentEvent.hasEvents(intent) && isEnabled(context, sleepSegment)) {
            Log.d(TAG, "Sleep segment event detected")
            val sleepSegmentEvent = SleepSegmentEvent.extractEvents(intent)
            if (sleepSegmentEvent.size > 0) {
                Log.d(TAG, "Sleep segment has an actual event")
                onSensorUpdated(
                    context,
                    sleepSegment,
                    sleepSegmentEvent.last().segmentDurationMillis,
                    sleepSegment.statelessIcon,
                    mapOf(
                        "start" to sleepSegmentEvent.last().startTimeMillis,
                        "end" to sleepSegmentEvent.last().endTimeMillis,
                        "status" to getSleepSegmentStatus(sleepSegmentEvent.last().status)
                    )
                )
            }
        }
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

    private fun getSleepSegmentStatus(int: Int): String {
        return when (int) {
            SleepSegmentEvent.STATUS_SUCCESSFUL -> "successful"
            SleepSegmentEvent.STATUS_MISSING_DATA -> "missing data"
            SleepSegmentEvent.STATUS_NOT_DETECTED -> "not detected"
            else -> STATE_UNKNOWN
        }
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#activity-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_activity

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(activity, sleepConfidence, sleepSegment)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            arrayOf()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        } else {
            true
        }
    }

    override fun requestSensorUpdate(context: Context) {
        if (isEnabled(context, activity)) {
            val actReg = ActivityRecognition.getClient(context)
            val pendingIntent = getActivityPendingIntent(context)
            Log.d(TAG, "Unregistering for activity updates.")
            actReg.removeActivityUpdates(pendingIntent)

            Log.d(TAG, "Registering for activity updates.")
            val fastUpdate = SensorReceiverBase.shouldDoFastUpdates(context)
            try {
                actReg.requestActivityUpdates(TimeUnit.MINUTES.toMillis(if (fastUpdate) 1 else 2), pendingIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to register for activity updates", e)
            }
        }
        if ((
            isEnabled(context, sleepConfidence) || isEnabled(
                    context,
                    sleepSegment
                )
            ) && !sleepRegistration
        ) {
            val pendingIntent = getSleepPendingIntent(context)
            Log.d(TAG, "Registering for sleep updates")
            try {
                val task = when {
                    (isEnabled(context, sleepConfidence) && !isEnabled(context, sleepSegment)) -> {
                        Log.d(TAG, "Registering for sleep confidence updates only")
                        ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                            pendingIntent,
                            SleepSegmentRequest(SleepSegmentRequest.CLASSIFY_EVENTS_ONLY)
                        )
                    }

                    (!isEnabled(context, sleepConfidence) && isEnabled(context, sleepSegment)) -> {
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
                task.addOnSuccessListener {
                    Log.d(TAG, "Successfully registered for sleep updates")
                    sleepRegistration = true
                }
                task.addOnFailureListener {
                    Log.e(TAG, "Failed to register for sleep updates", it)
                    ActivityRecognition.getClient(context).removeSleepSegmentUpdates(pendingIntent)
                    sleepRegistration = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to register for sleep updates", e)
            }
        }
    }

    private fun getSensorIcon(activity: String): String {
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
}
