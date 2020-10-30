package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.R

class SleepAsAndroidManager : SensorManager {
    companion object {
        private const val TAG = "SleepAsAndroid"

        val sleepTracking = SensorManager.BasicSensor(
            "sleep_tracking",
            "binary_sensor",
            R.string.basic_sensor_sleep_tracking,
            R.string.sensor_description_sleep_tracking
        )

        val lullaby = SensorManager.BasicSensor(
            "lullaby",
            "binary_sensor",
            R.string.basic_sensor_lullaby,
            R.string.sensor_description_lullaby
        )

        val sleepEvents = SensorManager.BasicSensor(
            "sleep_events",
            "sensor",
            R.string.basic_sensor_sleep_events,
            R.string.sensor_description_sleep_events
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_sleep_as_android

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(sleepTracking, lullaby, sleepEvents)

    override fun hasSensor(context: Context): Boolean {
        return try {
            context.packageManager.getApplicationInfo("com.urbandroid.sleep", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateSleepTracking(context, null)
        updateLullaby(context, null)
        updateSleepEvents(context, null)
    }

    fun updateSleepTracking(context: Context, intent: Intent?) {

        if (!isEnabled(context, sleepTracking.id))
            return

        if (intent == null)
            return

        val state: Boolean = intent.action == "com.urbandroid.sleep.alarmclock.SLEEP_TRACKING_STARTED_AUTO"

        val icon = if (state) "mdi:sleep" else "mdi:sleep-off"

        onSensorUpdated(context,
            sleepTracking,
            state,
            icon,
            mapOf()
        )
    }

    fun updateLullaby(context: Context, intent: Intent?) {

        if (!isEnabled(context, lullaby.id))
            return

        if (intent == null)
            return

        val state: Boolean = intent.action == "com.urbandroid.sleep.ACTION_LULLABY_START_PLAYBACK_AUTO"

        val icon = if (state) "mdi:sleep" else "mdi:sleep-off"

        onSensorUpdated(context,
            lullaby,
            state,
            icon,
            mapOf()
        )
    }

    fun updateSleepEvents(context: Context, intent: Intent?) {

        if (!isEnabled(context, sleepEvents.id))
            return

        if (intent == null)
            return

        val state = when (intent.action) {
            "com.urbandroid.sleep.TRACKING_DEEP_SLEEP_AUTO" -> "Deep sleep phase started"
            "com.urbandroid.sleep.TRACKING_LIGHT_SLEEP_AUTO" -> "Light sleep phase started"
            "com.urbandroid.sleep.alarmclock.ALARM_SNOOZE_CLICKED_ACTION_AUTO" -> "Snoozed by user"
            "com.urbandroid.sleep.alarmclock.TIME_TO_BED_ALARM_ALERT_AUTO" -> "Time to bed notification"
            "com.urbandroid.sleep.LUCID_CUE_ACTION_AUTO" -> "Lucid dreaming cue"
            "com.urbandroid.sleep.ANTISNORING_ACTION_AUTO" -> "Antisnoring sound"
            "com.urbandroid.sleep.audio.SOUND_EVENT_AUTO" -> "Audio recognition"
            "com.urbandroid.sleep.alarmclock.AUTO_START_SLEEP_TRACK_AUTO" -> "Automatic start of sleep tracking"
            "com.urbandroid.sleep.alarmclock.ALARM_ALERT_START_AUTO" -> "Alarm triggered"
            "com.urbandroid.sleep.alarmclock.ALARM_ALERT_DISMISS_AUTO" -> "Alarm dismissed"
            else -> "unknown"
        }

        val icon = "mdi:power-sleep"

        onSensorUpdated(context,
            sleepEvents,
            state,
            icon,
            mapOf()
        )
    }
}
