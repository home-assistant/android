package io.homeassistant.companion.android.sensors

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import io.homeassistant.companion.android.domain.integration.Sensor
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

    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        val sensorRegistrations = mutableListOf<SensorRegistration<Any>>()

        getNextAlarm(context)?.let {
            sensorRegistrations.add(
                SensorRegistration(
                    it,
                    "Next Alarm",
                    "timestamp"
                )
            )
        }

        return sensorRegistrations
    }

    override fun getSensors(context: Context): List<Sensor<Any>> {
        val sensors = mutableListOf<Sensor<Any>>()

        getNextAlarm(context)?.let {
            sensors.add(it)
        }

        return sensors
    }

    private fun getNextAlarm(context: Context): Sensor<Any>? {

        var triggerTime = 0L
        var local = ""
        var utc = "unavailable"

        try {
            val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the next alarm info", e)
        }

        val icon = "mdi:alarm"

        return Sensor(
            "next_alarm",
            utc,
            "sensor",
            icon,
            mapOf(
                "Local Time" to local,
                "Time in Milliseconds" to triggerTime
            )
        )
    }
}
