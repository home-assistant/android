package io.homeassistant.companion.android.common.sensors

import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class NextAlarmManager : SensorManager {
    companion object {
        private const val TAG = "NextAlarm"
        private const val SETTING_ALLOW_LIST = "nextalarm_allow_list"

        val nextAlarm = SensorManager.BasicSensor(
            "next_alarm",
            "sensor",
            commonR.string.basic_sensor_name_alarm,
            commonR.string.sensor_description_next_alarm,
            "mdi:alarm",
            deviceClass = "timestamp",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#next-alarm-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_alarm

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(nextAlarm)
    }

    override fun hasSensor(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        } else {
            true
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateNextAlarm(context)
    }

    private fun updateNextAlarm(context: Context) {
        if (!isEnabled(context, nextAlarm)) {
            return
        }

        var triggerTime = 0L
        var local = ""
        var utc = STATE_UNAVAILABLE
        var pendingIntent = ""

        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val sensorSetting = sensorDao.getSettings(nextAlarm.id)
        val allowPackageList = sensorSetting.firstOrNull { it.name == SETTING_ALLOW_LIST }?.value ?: ""

        try {
            val alarmManager = context.getSystemService<AlarmManager>()!!

            val alarmClockInfo = alarmManager.nextAlarmClock

            if (alarmClockInfo != null) {
                pendingIntent = alarmClockInfo.showIntent?.creatorPackage ?: STATE_UNKNOWN
                triggerTime = alarmClockInfo.triggerTime

                Log.d(TAG, "Next alarm is scheduled by $pendingIntent with trigger time $triggerTime")
                if (allowPackageList != "") {
                    val allowPackageListing = allowPackageList.split(", ")
                    if (pendingIntent !in allowPackageListing) {
                        Log.d(TAG, "Skipping update from $pendingIntent as it is not in the allow list")
                        return
                    }
                } else {
                    sensorDao.add(SensorSetting(nextAlarm.id, SETTING_ALLOW_LIST, allowPackageList, SensorSettingType.LIST_APPS))
                }

                val cal: Calendar = GregorianCalendar()
                cal.timeInMillis = triggerTime
                local = cal.time.toString()

                val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
                val sdf = SimpleDateFormat(dateFormat, Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                utc = sdf.format(Date(triggerTime))
            } else {
                Log.d(TAG, "No alarm is scheduled, sending unavailable")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the next alarm info", e)
        }

        onSensorUpdated(
            context,
            nextAlarm,
            utc,
            nextAlarm.statelessIcon,
            mapOf(
                "Local Time" to local,
                "Time in Milliseconds" to triggerTime,
                "Package" to pendingIntent
            )
        )
    }
}
