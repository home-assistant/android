package io.homeassistant.companion.android.common.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.database.sensor.toSensorWithAttributes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import timber.log.Timber

@Singleton
class LastRebootSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        private const val LOCAL_TIME = "Local Time"
        private const val SETTING_DEADBAND = "lastreboot_deadband"
        private const val TIME_MILLISECONDS = "Time in Milliseconds"

        @ProvidesSensor
        internal val lastRebootSensor = SensorManager.BasicSensor(
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

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(lastRebootSensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        updateLastReboot(applicationContext)
    }

    @SuppressLint("SimpleDateFormat")
    private suspend fun updateLastReboot(applicationContext: Context) {
        if (!isEnabled(lastRebootSensor)) {
            return
        }

        var timeInMillis = 0L
        var local = ""
        var utc = STATE_UNAVAILABLE

        val sensorRepository = sensorRepository
        val fullSensor = sensorRepository.getFull(lastRebootSensor.id).toSensorWithAttributes()
        val sensorSetting = sensorRepository.getSettings(lastRebootSensor.id)
        val lastTimeMillis =
            fullSensor?.attributes?.firstOrNull { it.name == TIME_MILLISECONDS }?.value?.toLongOrNull() ?: 0L
        val settingDeadband = sensorSetting.firstOrNull { it.name == SETTING_DEADBAND }?.value?.toIntOrNull() ?: 60000
        sensorRepository.add(
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
