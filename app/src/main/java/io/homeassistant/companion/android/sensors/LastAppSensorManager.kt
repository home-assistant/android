package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class LastAppSensorManager : SensorManager {
    companion object {
        private const val TAG = "LastApp"

        val last_used = SensorManager.BasicSensor(
            "last_used_app",
            "sensor",
            commonR.string.basic_sensor_name_last_used_app,
            commonR.string.sensor_description_last_used_app,
            "mdi:android"
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#last-used-app-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_last_app

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(last_used)
    }

    override fun hasSensor(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
    @RequiresApi(Build.VERSION_CODES.M)
    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.PACKAGE_USAGE_STATS)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    override fun requestSensorUpdate(
        context: Context
    ) {
        updateLastApp(context)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private fun updateLastApp(context: Context) {
        if (!isEnabled(context, last_used.id))
            return

        val usageStats = context.getSystemService<UsageStatsManager>()!!
        val current = System.currentTimeMillis()
        val lastApp = usageStats.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, current - 1000 * 1000, current).maxByOrNull { it.lastTimeUsed }?.packageName ?: "none"

        var appLabel = "unknown"

        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(lastApp, PackageManager.GET_META_DATA)
            appLabel = pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get package label for: $lastApp", e)
        }

        onSensorUpdated(
            context,
            last_used,
            lastApp,
            last_used.statelessIcon,
            mapOf(
                "Label" to appLabel
            )
        )
    }
}
