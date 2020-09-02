package io.homeassistant.companion.android.sensors

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

class NextAlarmManager : SensorManager {
    companion object {
        private const val TAG = "NextAlarm"

        private val nextAlarm = SensorManager.BasicSensor(
            "next_alarm",
            "sensor",
            R.string.basic_sensor_name_alarm,
            R.string.sensor_description_next_alarm,
            "timestamp"
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_alarm

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(nextAlarm)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateNextAlarm(context)
    }

    private fun updateNextAlarm(context: Context) {

        if (!isEnabled(context, nextAlarm.id))
            return

        var triggerTime = 0L
        var local = ""
        var utc = "unavailable"
        var pendingIntent = ""

        try {
            val alarmManager: AlarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val alarmClockInfo = alarmManager.nextAlarmClock

            if (alarmClockInfo != null) {
                triggerTime = alarmClockInfo.triggerTime

                val cal: Calendar = GregorianCalendar()
                cal.timeInMillis = triggerTime
                local = cal.time.toString()

                val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
                val sdf = SimpleDateFormat(dateFormat)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                utc = sdf.format(Date(triggerTime))

                pendingIntent = alarmClockInfo.showIntent?.creatorPackage ?: "Unknown"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the next alarm info", e)
        }

        val icon = "mdi:alarm"

        onSensorUpdated(context,
            nextAlarm,
            utc,
            icon,
            mapOf(
                "Local Time" to local,
                "Time in Milliseconds" to triggerTime,
                "Package" to pendingIntent
            )
        )
    }
}
