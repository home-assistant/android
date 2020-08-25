package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

class LastRebootSensorManager : SensorManager {
    companion object {
        private const val TAG = "LastReboot"

        private val lastRebootSensor = SensorManager.BasicSensor(
            "last_reboot",
            "sensor",
            "Last Reboot",
            "timestamp"
        )
    }

    override val name: String
        get() = "Last Reboot Sensor"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(lastRebootSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            lastRebootSensor.id -> getLastReboot()
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getLastReboot(): SensorRegistration<Any> {

        var timeInMillis = 0L
        var local = ""
        var utc = "unavailable"

        try {
            timeInMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val cal: Calendar = GregorianCalendar()
            cal.timeInMillis = timeInMillis
            local = cal.time.toString()

            val dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
            val sdf = SimpleDateFormat(dateFormat)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            utc = sdf.format(Date(timeInMillis))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the last reboot timestamp", e)
        }

        val icon = "mdi:restart"

        return lastRebootSensor.toSensorRegistration(
            utc,
            icon,
            mapOf(
                "Local Time" to local,
                "Time in Milliseconds" to timeInMillis
            )
        )
    }
}
