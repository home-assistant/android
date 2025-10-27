package io.homeassistant.companion.android.common.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.sensor.toSensorWithAttributes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.absoluteValue
import timber.log.Timber

class LastRebootSensorManager : SensorManager {
    companion object {
        private const val LOCAL_TIME = "Local Time"
        private const val SETTING_DEADBAND = "lastreboot_deadband"
        private const val TIME_MILLISECONDS = "Time in Milliseconds"

        private val lastRebootSensor = SensorManager.BasicSensor(
            "last_reboot",
            "sensor",
            commonR.string.basic_sensor_name_last_reboot,
            commonR.string.sensor_description_last_reboot,
            "mdi:restart",
            deviceClass = "timestamp",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#last-reboot-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_last_reboot

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(lastRebootSensor)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        updateLastReboot(context)
    }

    @SuppressLint("SimpleDateFormat")
    private suspend fun updateLastReboot(context: Context) {
        if (!isEnabled(context, lastRebootSensor)) {
            return
        }

        var timeInMillis = 0L
        var local = ""
        var utc = STATE_UNAVAILABLE

        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val fullSensor = sensorDao.getFull(lastRebootSensor.id).toSensorWithAttributes()
        val sensorSetting = sensorDao.getSettings(lastRebootSensor.id)
        val lastTimeMillis =
            fullSensor?.attributes?.firstOrNull { it.name == TIME_MILLISECONDS }?.value?.toLongOrNull() ?: 0L
        val settingDeadband = sensorSetting.firstOrNull { it.name == SETTING_DEADBAND }?.value?.toIntOrNull() ?: 60000
        sensorDao.add(
            SensorSetting(lastRebootSensor.id, SETTING_DEADBAND, settingDeadband.toString(), SensorSettingType.NUMBER),
        )
        try {
            timeInMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val diffMillis = (timeInMillis - lastTimeMillis).absoluteValue
            if (lastTimeMillis != 0L && settingDeadband > diffMillis) {
                return
            }
            val cal: Calendar = GregorianCalendar()
            cal.timeInMillis = timeInMillis
            local = cal.time.toString()

            val dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
            val sdf = SimpleDateFormat(dateFormat)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            utc = sdf.format(Date(timeInMillis))
        } catch (e: Exception) {
            Timber.e(e, "Error getting the last reboot timestamp")
        }

        onSensorUpdated(
            context,
            lastRebootSensor,
            utc,
            lastRebootSensor.statelessIcon,
            mapOf(
                LOCAL_TIME to local,
                TIME_MILLISECONDS to timeInMillis,
            ),
        )
    }
}
