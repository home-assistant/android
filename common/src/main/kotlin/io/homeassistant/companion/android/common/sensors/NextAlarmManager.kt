package io.homeassistant.companion.android.common.sensors

import android.app.AlarmManager
import android.content.Context
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class NextAlarmManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        private const val SETTING_ALLOW_LIST = "nextalarm_allow_list"

        @ProvidesSensor
        val nextAlarm = SensorManager.BasicSensor(
            "next_alarm",
            "sensor",
            commonR.string.basic_sensor_name_alarm,
            commonR.string.sensor_description_next_alarm,
            "mdi:alarm",
            deviceClass = "timestamp",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#next-alarm-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_alarm

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(nextAlarm)
    }

    override fun hasSensor(): Boolean {
        return !applicationContext.isAutomotive()
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        updateNextAlarm()
    }

    private suspend fun updateNextAlarm() {
        if (!isEnabled(nextAlarm)) {
            return
        }

        var triggerTime = 0L
        var local = ""
        var utc = STATE_UNAVAILABLE
        var pendingIntent = ""

        val sensorRepository = sensorRepository
        val sensorSetting = sensorRepository.getSettings(nextAlarm.id)
        val allowPackageList = sensorSetting.firstOrNull { it.name == SETTING_ALLOW_LIST }?.value ?: ""

        try {
            val alarmManager = applicationContext.getSystemService<AlarmManager>()!!

            val alarmClockInfo = alarmManager.nextAlarmClock

            if (alarmClockInfo != null) {
                pendingIntent = alarmClockInfo.showIntent?.creatorPackage ?: STATE_UNKNOWN
                triggerTime = alarmClockInfo.triggerTime

                Timber.d("Next alarm is scheduled by $pendingIntent with trigger time $triggerTime")
                if (allowPackageList != "") {
                    val allowPackageListing = allowPackageList.split(", ")
                    if (pendingIntent !in allowPackageListing) {
                        Timber.d("Skipping update from $pendingIntent as it is not in the allow list")
                        return
                    }
                } else {
                    sensorRepository.add(
                        SensorSetting(nextAlarm.id, SETTING_ALLOW_LIST, allowPackageList, SensorSettingType.LIST_APPS),
                    )
                }

                val cal: Calendar = GregorianCalendar()
                cal.timeInMillis = triggerTime
                local = cal.time.toString()

                val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
                val sdf = SimpleDateFormat(dateFormat, Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                utc = sdf.format(Date(triggerTime))
            } else {
                Timber.d("No alarm is scheduled, sending unavailable")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting the next alarm info")
        }

        onSensorUpdated(
            nextAlarm,
            utc,
            nextAlarm.statelessIcon,
            mapOf(
                "Local Time" to local,
                "Time in Milliseconds" to triggerTime,
                "Package" to pendingIntent,
            ),
        )
    }
}
