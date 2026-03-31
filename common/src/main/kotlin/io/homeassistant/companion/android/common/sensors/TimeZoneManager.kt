package io.homeassistant.companion.android.common.sensors

import android.content.Context
import io.homeassistant.companion.android.common.R as commonR
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TimeZoneManager : SensorManager {
    companion object {
        val currentTimeZone = SensorManager.BasicSensor(
            "current_time_zone",
            "sensor",
            commonR.string.basic_sensor_name_current_time_zone,
            commonR.string.sensor_description_current_time_zone,
            "mdi:map-clock",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#current-time-zone-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_time_zone

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(currentTimeZone)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        updateTimeZone(context)
    }

    private suspend fun updateTimeZone(context: Context) {
        if (!isEnabled(context, currentTimeZone)) {
            return
        }

        val timeZone: TimeZone = TimeZone.getDefault()
        val currentlyInDst = timeZone.inDaylightTime(Date())

        onSensorUpdated(
            context,
            currentTimeZone,
            timeZone.getDisplayName(currentlyInDst, TimeZone.LONG, Locale.ENGLISH),
            currentTimeZone.statelessIcon,
            mapOf(
                "in_daylight_time" to currentlyInDst,
                "time_zone_id" to timeZone.id,
                "time_zone_short" to timeZone.getDisplayName(currentlyInDst, TimeZone.SHORT, Locale.ENGLISH),
                "uses_daylight_time" to timeZone.useDaylightTime(),
                "utc_offset" to timeZone.getOffset(System.currentTimeMillis()),
            ),
        )
    }
}
