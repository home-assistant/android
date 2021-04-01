package io.homeassistant.companion.android.sensors

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.R
import java.math.RoundingMode

class AppSensorManager : SensorManager {
    companion object {
        private const val TAG = "AppSensor"
        private const val GB = 1000000000

        val currentVersion = SensorManager.BasicSensor(
            "current_version",
            "sensor",
            R.string.basic_sensor_name_current_version,
            R.string.sensor_description_current_version,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#current-version-sensor"
        )

        val app_rx_gb = SensorManager.BasicSensor(
            "app_rx_gb",
            "sensor",
            R.string.basic_sensor_name_app_rx_gb,
            R.string.sensor_description_app_rx_gb,
            unitOfMeasurement = "GB",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-data-sensors"
        )

        val app_tx_gb = SensorManager.BasicSensor(
            "app_tx_gb",
            "sensor",
            R.string.basic_sensor_name_app_tx_gb,
            R.string.sensor_description_app_tx_gb,
            unitOfMeasurement = "GB",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-data-sensors"
        )

        val app_memory = SensorManager.BasicSensor(
            "app_memory",
            "sensor",
            R.string.basic_sensor_name_app_memory,
            R.string.sensor_description_app_memory,
            unitOfMeasurement = "GB",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-memory-sensor"
        )

        val app_inactive = SensorManager.BasicSensor(
            "app_inactive",
            "binary_sensor",
            R.string.basic_sensor_name_app_inactive,
            R.string.sensor_description_app_inactive,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-usage-sensors"
        )

        val app_standby_bucket = SensorManager.BasicSensor(
            "app_standby_bucket",
            "sensor",
            R.string.basic_sensor_name_app_standby,
            R.string.sensor_description_app_standby,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-usage-sensors"
        )

        val app_importance = SensorManager.BasicSensor(
            "app_importance",
            "sensor",
            R.string.basic_sensor_name_app_importance,
            R.string.sensor_description_app_importance,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-importance-sensor"
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_app_sensor

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return when {
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ->
                listOf(
                    currentVersion, app_rx_gb, app_tx_gb, app_memory, app_inactive,
                    app_standby_bucket, app_importance
                )
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ->
                listOf(
                    currentVersion, app_rx_gb, app_tx_gb, app_memory, app_inactive,
                    app_importance
                )
            else -> listOf(currentVersion, app_rx_gb, app_tx_gb, app_memory, app_importance)
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        val myUid = Process.myUid()
        updateCurrentVersion(context)
        updateAppMemory(context)
        updateAppRxGb(context, myUid)
        updateAppTxGb(context, myUid)
        updateImportanceCheck(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            updateAppInactive(context, usageStatsManager)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                updateAppStandbyBucket(context, usageStatsManager)
        }
    }

    private fun updateCurrentVersion(context: Context) {

        if (!isEnabled(context, currentVersion.id))
            return

        val state = BuildConfig.VERSION_NAME
        val icon = "mdi:android"

        onSensorUpdated(context,
            currentVersion,
            state,
            icon,
            mapOf()
        )
    }

    private fun updateAppRxGb(context: Context, appUid: Int) {

        if (!isEnabled(context, app_rx_gb.id))
            return

        val appRx = try {
            TrafficStats.getUidRxBytes(appUid).toFloat() / GB
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app rx bytes", e)
            return
        }
        val icon = "mdi:radio-tower"

        onSensorUpdated(
            context,
            app_rx_gb,
            appRx.toBigDecimal().setScale(4, RoundingMode.HALF_EVEN),
            icon,
            mapOf()
        )
    }

    private fun updateAppTxGb(context: Context, appUid: Int) {

        if (!isEnabled(context, app_tx_gb.id))
            return

        val appTx = try {
            TrafficStats.getUidTxBytes(appUid).toFloat() / GB
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app tx bytes", e)
            return
        }
        val icon = "mdi:radio-tower"

        onSensorUpdated(
            context,
            app_tx_gb,
            appTx.toBigDecimal().setScale(4, RoundingMode.HALF_EVEN),
            icon,
            mapOf()
        )
    }

    private fun updateAppMemory(context: Context) {

        if (!isEnabled(context, app_memory.id))
            return

        val runTime = Runtime.getRuntime()
        val freeSize = runTime.freeMemory().toFloat() / GB
        val totalSize = runTime.totalMemory().toFloat() / GB
        val usedSize = totalSize - freeSize

        val icon = "mdi:memory"

        onSensorUpdated(
            context,
            app_memory,
            usedSize.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            icon,
            mapOf(
                "free_memory" to freeSize.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
                "total_memory" to totalSize.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN)
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun updateAppInactive(context: Context, usageStatsManager: UsageStatsManager) {
        if (!isEnabled(context, app_inactive.id))
            return

        val isAppInactive = usageStatsManager.isAppInactive(context.packageName)

        val icon = if (isAppInactive) "mdi:timer-off-outline" else "mdi:timer-outline"

        onSensorUpdated(
            context,
            app_inactive,
            isAppInactive,
            icon,
            mapOf()
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun updateAppStandbyBucket(context: Context, usageStatsManager: UsageStatsManager) {
        if (!isEnabled(context, app_standby_bucket.id))
            return

        val appStandbyBucket = when (usageStatsManager.appStandbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
            else -> "never"
        }

        val icon = "mdi:android"

        onSensorUpdated(
            context,
            app_standby_bucket,
            appStandbyBucket,
            icon,
            mapOf()
        )
    }

    private fun updateImportanceCheck(context: Context) {
        if (!isEnabled(context, app_importance.id))
            return

        val appManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val currentProcess = appManager.runningAppProcesses
        var importance = "not_running"
        if (currentProcess != null) {
            for (item in currentProcess) {
                if (context.applicationInfo.processName == item.processName) {
                    importance = when (item.importance) {
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> {
                            "cached"
                        }
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> {
                            "cant_save_state"
                        }
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> {
                            "foreground"
                        }
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> {
                            "foreground_service"
                        }
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> {
                            "gone"
                        }
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> {
                            "perceptible"
                        }
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> {
                            "service"
                        }
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> {
                            "top_sleeping"
                        }
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> {
                            "visible"
                        }
                        else -> "not_running"
                    }
                }
            }
        }
        val icon = "mdi:android"

        onSensorUpdated(
            context,
            app_importance,
            importance,
            icon,
            mapOf()
        )
    }
}
