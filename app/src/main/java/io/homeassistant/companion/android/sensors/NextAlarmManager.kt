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

        private val nextAlarm = SensorManager.BasicSensor(
            "next_alarm",
            "sensor",
            "Next Alarm",
            "timestamp"
        )
    }

    override val name: String
        get() = "Alarm Sensors"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(nextAlarm)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            nextAlarm.id -> getNextAlarm(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getNextAlarm(context: Context): SensorRegistration<Any> {

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

        return nextAlarm.toSensorRegistration(
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
