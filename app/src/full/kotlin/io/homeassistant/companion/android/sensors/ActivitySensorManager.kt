package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorReceiverBase
import io.homeassistant.companion.android.common.util.isAutomotive
import java.util.concurrent.TimeUnit
import timber.log.Timber

class ActivitySensorManager : SensorManager {

    companion object {
        const val ACTION_UPDATE_ACTIVITY =
            "io.homeassistant.companion.android.background.UPDATE_ACTIVITY"

        const val ACTION_SLEEP_ACTIVITY =
            "io.homeassistant.companion.android.background.SLEEP_ACTIVITY"
        private var sleepRegistration = false

        internal val activity = SensorManager.BasicSensor(
            "detected_activity",
            "sensor",
            commonR.string.basic_sensor_name_activity,
            commonR.string.sensor_description_detected_activity,
            "mdi:walk",
            deviceClass = "enum",
        )

        internal val sleepConfidence = SensorManager.BasicSensor(
            "sleep_confidence",
            "sensor",
            commonR.string.basic_sensor_name_sleep_confidence,
            commonR.string.sensor_description_sleep_confidence,
            "mdi:sleep",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            updateType = SensorManager.BasicSensor.UpdateType.CUSTOM,
        )

        internal val sleepSegment = SensorManager.BasicSensor(
            "sleep_segment",
            "sensor",
            commonR.string.basic_sensor_name_sleep_segment,
            commonR.string.sensor_description_sleep_segment,
            "mdi:sleep",
            unitOfMeasurement = "ms",
            deviceClass = "duration",
            updateType = SensorManager.BasicSensor.UpdateType.CUSTOM,
        )

        internal val ACTIVITY_OPTIONS = listOf(
            "in_vehicle", "on_bicycle", "on_foot", "still", "tilting", "walking", "running",
        )

        internal fun getSensorIcon(activity: String): String {
            return when (activity) {
                "in_vehicle" -> "mdi:car"
                "on_bicycle" -> "mdi:bike"
                "on_foot" -> "mdi:shoe-print"
                "still" -> "mdi:human-male"
                "tilting" -> "mdi:phone-rotate-portrait"
                "walking" -> "mdi:walk"
                "running" -> "mdi:run"
                else -> "mdi:progress-question"
            }
        }
    }

    private fun getActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivitySensorBroadcastReceiver::class.java)
        intent.action = ACTION_UPDATE_ACTIVITY
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun getSleepPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivitySensorBroadcastReceiver::class.java)
        intent.action = ACTION_SLEEP_ACTIVITY
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#activity-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_activity

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(activity, sleepConfidence, sleepSegment)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION,
            )
        } else {
            arrayOf()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        return !context.isAutomotive()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        if (isEnabled(context, activity)) {
            val actReg = ActivityRecognition.getClient(context)
            val pendingIntent = getActivityPendingIntent(context)
            Timber.d("Unregistering for activity updates.")
            actReg.removeActivityUpdates(pendingIntent)

            Timber.d("Registering for activity updates.")
            val fastUpdate = SensorReceiverBase.shouldDoFastUpdates(context)
            try {
                actReg.requestActivityUpdates(TimeUnit.MINUTES.toMillis(if (fastUpdate) 1 else 2), pendingIntent)
            } catch (e: Exception) {
                Timber.e(e, "Unable to register for activity updates")
            }
        }
        if ((
                isEnabled(context, sleepConfidence) ||
                    isEnabled(
                        context,
                        sleepSegment,
                    )
                ) &&
            !sleepRegistration
        ) {
            val pendingIntent = getSleepPendingIntent(context)
            Timber.d("Registering for sleep updates")
            try {
                val task = when {
                    (isEnabled(context, sleepConfidence) && !isEnabled(context, sleepSegment)) -> {
                        Timber.d("Registering for sleep confidence updates only")
                        ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                            pendingIntent,
                            SleepSegmentRequest(SleepSegmentRequest.CLASSIFY_EVENTS_ONLY),
                        )
                    }

                    (!isEnabled(context, sleepConfidence) && isEnabled(context, sleepSegment)) -> {
                        Timber.d("Registering for sleep segment updates only")
                        ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                            pendingIntent,
                            SleepSegmentRequest(SleepSegmentRequest.SEGMENT_EVENTS_ONLY),
                        )
                    }

                    else -> {
                        Timber.d("Registering for both sleep confidence and segment updates")
                        ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                            pendingIntent,
                            SleepSegmentRequest.getDefaultSleepSegmentRequest(),
                        )
                    }
                }
                task.addOnSuccessListener {
                    Timber.d("Successfully registered for sleep updates")
                    sleepRegistration = true
                }
                task.addOnFailureListener {
                    Timber.e(it, "Failed to register for sleep updates")
                    ActivityRecognition.getClient(context).removeSleepSegmentUpdates(pendingIntent)
                    sleepRegistration = false
                }
            } catch (e: Exception) {
                Timber.e(e, "Unable to register for sleep updates")
            }
        }
    }
}
