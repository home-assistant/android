package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.google.sensors.ActivityIntentData
import io.homeassistant.companion.android.google.sensors.enableActivityUpdates
import io.homeassistant.companion.android.google.sensors.enableSleepUpdates
import io.homeassistant.companion.android.google.sensors.getActivityAttributes
import io.homeassistant.companion.android.google.sensors.getProbActivity
import io.homeassistant.companion.android.google.sensors.getSensorIcon
import io.homeassistant.companion.android.google.sensors.getSleepClassifyEvent
import io.homeassistant.companion.android.google.sensors.getSleepPendingIntent
import io.homeassistant.companion.android.google.sensors.getSleepSegmentEvent
import io.homeassistant.companion.android.google.sensors.getSleepSegmentStatus
import io.homeassistant.companion.android.google.sensors.hasSleepClassifyEvents
import io.homeassistant.companion.android.google.sensors.hasSleepSegmentEvent
import io.homeassistant.companion.android.google.sensors.removeSleepUpdates
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ActivitySensorManager : BroadcastReceiver(), SensorManager {

    companion object {

        internal const val TAG = "ActivitySM"
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
            ActivityIntentData.ACTION_UPDATE_ACTIVITY -> handleActivityUpdate(intent, context)
            ActivityIntentData.ACTION_SLEEP_ACTIVITY -> handleSleepUpdate(intent, context)
            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun handleActivityUpdate(intent: Intent, context: Context) {
        Log.d(TAG, "Received activity update")
        val probActivity = getProbActivity(intent)
        val activityAttributes = getActivityAttributes(intent)
        if (probActivity != null && activityAttributes != null)
            onSensorUpdated(
                context,
                activity,
                probActivity,
                getSensorIcon(probActivity),
                activityAttributes
            )
    }

    private fun handleSleepUpdate(intent: Intent, context: Context) {
        Log.d(TAG, "Received sleep update")
        if (hasSleepClassifyEvents(intent) && isEnabled(context, sleepConfidence)) {
            Log.d(TAG, "Sleep classify event detected")
            val sleepClassifyEvent = getSleepClassifyEvent(intent)
            if (!sleepClassifyEvent.isNullOrEmpty()) {
                Log.d(TAG, "Sleep classify has an actual event")
                onSensorUpdated(
                    context,
                    sleepConfidence,
                    sleepClassifyEvent["confidence"]!!,
                    sleepConfidence.statelessIcon,
                    mapOf(
                        "light" to sleepClassifyEvent["light"],
                        "motion" to sleepClassifyEvent["motion"],
                        "timestamp" to sleepClassifyEvent["timestamp"]
                    )
                )

                // Send the update immediately
                SensorReceiver.updateAllSensors(context)
            }
        }
        if (hasSleepSegmentEvent(intent) && isEnabled(context, sleepSegment)) {
            Log.d(TAG, "Sleep segment event detected")
            val sleepSegmentEvent = getSleepSegmentEvent(intent)
            if (!sleepSegmentEvent.isNullOrEmpty()) {
                Log.d(TAG, "Sleep segment has an actual event")
                onSensorUpdated(
                    context,
                    sleepSegment,
                    sleepSegmentEvent["duration"]!!,
                    sleepSegment.statelessIcon,
                    mapOf(
                        "start" to sleepSegmentEvent["start"],
                        "end" to sleepSegmentEvent["end"],
                        "status" to getSleepSegmentStatus(sleepSegmentEvent["status"] as Int)
                    )
                )
            }
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

    override fun requestSensorUpdate(context: Context) {
        if (isEnabled(context, activity)) {
            enableActivityUpdates(context, ActivitySensorManager::class.java)
        }
        if (
            (
                isEnabled(context, sleepConfidence) ||
                    isEnabled(context, sleepSegment)
                ) && !sleepRegistration
        ) {
            val pendingIntent = getSleepPendingIntent(context, ActivitySensorManager::class.java)
            Log.d(TAG, "Registering for sleep updates")
            val task = enableSleepUpdates(
                context,
                if (isEnabled(context, sleepConfidence) &&
                    !isEnabled(context, sleepSegment)
                )
                    "classify_only"
                else if (
                    !isEnabled(context, sleepConfidence) &&
                    isEnabled(context, sleepSegment)
                )
                    "events_only"
                else
                    "both",
                pendingIntent
            )
            task.addOnSuccessListener {
                Log.d(TAG, "Successfully registered for sleep updates")
                sleepRegistration = true
            }
            task.addOnFailureListener {
                Log.e(TAG, "Failed to register for sleep updates", it)
                removeSleepUpdates(context, pendingIntent)
                sleepRegistration = false
            }
        }
    }
}
