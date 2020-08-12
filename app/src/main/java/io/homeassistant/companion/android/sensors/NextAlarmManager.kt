package io.homeassistant.companion.android.sensors

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

class NextAlarmManager : SensorManager {
    companion object {

        private const val TAG = "NextAlarm"
    }

    override val name: String
        get() = "Alarm Sensors"

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        return listOf(getNextAlarm(context))
    }

    private fun getNextAlarm(context: Context): SensorRegistration<Any> {

        var triggerTime = 0L
        var local = ""
        var utc = "unavailable"
        var pendingIntent = ""

        try {
            val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val alarmClockInfo = alarmManager.nextAlarmClock

            if (alarmClockInfo != null) {
                triggerTime = alarmClockInfo.triggerTime
                pendingIntent = alarmClockInfo.showIntent.creatorPackage.toString()

                val cal: Calendar = GregorianCalendar()
                cal.timeInMillis = triggerTime
                local = cal.time.toString()

                val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
                val sdf = SimpleDateFormat(dateFormat)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                utc = sdf.format(Date(triggerTime))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the next alarm info", e)
        }

        val icon = "mdi:alarm"

        return SensorRegistration(
            "next_alarm",
            utc,
            "sensor",
            icon,
            mapOf(
                "Local Time" to local,
                "Time in Milliseconds" to triggerTime,
                "Package" to pendingIntent
            ),
            "Next Alarm",
            "timestamp"
        )
    }
}
