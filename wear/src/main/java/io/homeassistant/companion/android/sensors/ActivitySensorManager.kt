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
import io.homeassistant.companion.android.google.sensors.getActivityAttributes
import io.homeassistant.companion.android.google.sensors.getProbActivity
import io.homeassistant.companion.android.google.sensors.getSensorIcon
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ActivitySensorManager : BroadcastReceiver(), SensorManager {

    companion object {

        internal const val TAG = "ActivitySM"

        private val activity = SensorManager.BasicSensor(
            "detected_activity",
            "sensor",
            commonR.string.basic_sensor_name_activity,
            commonR.string.sensor_description_detected_activity,
            "mdi:walk"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {

        when (intent.action) {
            ActivityIntentData.ACTION_UPDATE_ACTIVITY -> handleActivityUpdate(intent, context)
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

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#activity-sensors"
    }

    override val name: Int
        get() = commonR.string.sensor_name_activity

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(activity)
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
        enableActivityUpdates(context, ActivitySensorManager::class.java, isEnabled(context, activity))
    }
}
