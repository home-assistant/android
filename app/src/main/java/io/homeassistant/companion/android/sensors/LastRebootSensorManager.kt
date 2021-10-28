package io.homeassistant.companion.android.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Setting
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.absoluteValue

class LastRebootSensorManager : SensorManager {
    companion object {
        private const val TAG = "LastReboot"
        private const val LOCAL_TIME = "Local Time"
        private const val SETTING_DEADBAND = "lastreboot_deadband"
        private const val TIME_MILLISECONDS = "Time in Milliseconds"

        private val lastRebootSensor = SensorManager.BasicSensor(
            "last_reboot",
            "sensor",
            R.string.basic_sensor_name_last_reboot,
            R.string.sensor_description_last_reboot,
            "timestamp",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#last-reboot-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_last_reboot

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(lastRebootSensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateLastReboot(context)
    }

    @SuppressLint("SimpleDateFormat")
    private fun updateLastReboot(context: Context) {
        if (!isEnabled(context, lastRebootSensor.id))
            return

        var timeInMillis = 0L
        var local = ""
        var utc = "unavailable"

        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val fullSensor = sensorDao.getFull(lastRebootSensor.id)
        val sensorSetting = sensorDao.getSettings(lastRebootSensor.id)
        val lastTimeMillis = fullSensor?.attributes?.firstOrNull { it.name == TIME_MILLISECONDS }?.value?.toLongOrNull() ?: 0L
        val settingDeadband = sensorSetting.firstOrNull { it.name == SETTING_DEADBAND }?.value?.toIntOrNull() ?: 60000
        sensorDao.add(Setting(lastRebootSensor.id, SETTING_DEADBAND, settingDeadband.toString(), "number"))
        try {
            timeInMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val diffMillis = (timeInMillis - lastTimeMillis).absoluteValue
            if (lastTimeMillis != 0L && settingDeadband > diffMillis)
                return
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
                LOCAL_TIME to local,
                TIME_MILLISECONDS to timeInMillis
            )
        )
    }
}
