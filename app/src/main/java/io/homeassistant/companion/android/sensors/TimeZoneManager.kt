package io.homeassistant.companion.android.sensors

import android.content.Context
import io.homeassistant.companion.android.common.sensors.SensorManager
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import io.homeassistant.companion.android.common.R as commonR

class TimeZoneManager : SensorManager {
    companion object {
        private const val TAG = "TimeZone"

        val currentTimeZone = SensorManager.BasicSensor(
            "current_time_zone",
            "sensor",
            commonR.string.basic_sensor_name_current_time_zone,
            commonR.string.sensor_description_current_time_zone,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#current-time-zone-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_time_zone

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(currentTimeZone)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateTimeZone(context)
    }

    private fun updateTimeZone(context: Context) {

        if (!isEnabled(context, currentTimeZone.id))
            return

        val timeZone: TimeZone = TimeZone.getDefault()
        val currentZone = timeZone.getDisplayName(Locale.ENGLISH)
        val date = Date()

        val icon = "mdi:map-clock"

        onSensorUpdated(
            context,
            currentTimeZone,
            currentZone,
            icon,
            mapOf(
                "in_daylight_time" to timeZone.inDaylightTime(date),
                "time_zone_id" to timeZone.id,
                "time_zone_short" to timeZone.getDisplayName(timeZone.inDaylightTime(date), TimeZone.SHORT, Locale.ENGLISH),
                "uses_daylight_time" to timeZone.useDaylightTime(),
                "utc_offset" to timeZone.getOffset(System.currentTimeMillis())
            )
        )
    }
}
