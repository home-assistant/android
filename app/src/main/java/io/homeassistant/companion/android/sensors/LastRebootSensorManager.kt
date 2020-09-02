package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.homeassistant.companion.android.R
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
            R.string.basic_sensor_name_last_reboot,
            R.string.sensor_description_last_reboot,
            "timestamp"
        )
    }

    override val name: Int
        get() = R.string.sensor_name_last_reboot

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(lastRebootSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateLastReboot(context)
    }

    private fun updateLastReboot(context: Context) {
        if (!isEnabled(context, lastRebootSensor.id))
            return

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

        onSensorUpdated(
            context,
            lastRebootSensor,
            utc,
            icon,
            mapOf(
                "Local Time" to local,
                "Time in Milliseconds" to timeInMillis
            )
        )
    }
}
