package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.isAutomotive
import timber.log.Timber

class LastAppSensorManager : SensorManager {
    companion object {
        val last_used = SensorManager.BasicSensor(
            "last_used_app",
            "sensor",
            commonR.string.basic_sensor_name_last_used_app,
            commonR.string.sensor_description_last_used_app,
            "mdi:android",
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#last-used-app-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_last_app

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(last_used)
    }

    override fun hasSensor(context: Context): Boolean {
        return !context.isAutomotive()
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.PACKAGE_USAGE_STATS)
    }

    override suspend fun requestSensorUpdate(context: Context) {
        updateLastApp(context)
    }

    private suspend fun updateLastApp(context: Context) {
        if (!isEnabled(context, last_used)) {
            return
        }

        val usageStats = context.getSystemService<UsageStatsManager>()!!
        val current = System.currentTimeMillis()
        val lastApp =
            usageStats.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, current - 1000 * 1000, current).maxByOrNull {
                it.lastTimeUsed
            }?.packageName
                ?: "none"

        var appLabel = STATE_UNKNOWN

        try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    lastApp,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(lastApp, PackageManager.GET_META_DATA)
            }
            appLabel = pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Timber.e(e, "Unable to get package label for: $lastApp")
        }

        onSensorUpdated(
            context,
            last_used,
            lastApp,
            last_used.statelessIcon,
            mapOf(
                "Label" to appLabel,
            ),
        )
    }
}
