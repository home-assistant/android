package io.homeassistant.companion.android.common.sensors

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import java.math.RoundingMode
import timber.log.Timber

abstract class AppSensorManagerBase : SensorManager {
    companion object {
        private const val GB = 1000000000

        val currentVersion = SensorManager.BasicSensor(
            "current_version",
            "sensor",
            commonR.string.basic_sensor_name_current_version,
            commonR.string.sensor_description_current_version,
            "mdi:android",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#current-version-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        val app_rx_gb = SensorManager.BasicSensor(
            "app_rx_gb",
            "sensor",
            commonR.string.basic_sensor_name_app_rx_gb,
            commonR.string.sensor_description_app_rx_gb,
            "mdi:radio-tower",
            unitOfMeasurement = "GB",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-data-sensors",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        val app_tx_gb = SensorManager.BasicSensor(
            "app_tx_gb",
            "sensor",
            commonR.string.basic_sensor_name_app_tx_gb,
            commonR.string.sensor_description_app_tx_gb,
            "mdi:radio-tower",
            unitOfMeasurement = "GB",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-data-sensors",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        val app_memory = SensorManager.BasicSensor(
            "app_memory",
            "sensor",
            commonR.string.basic_sensor_name_app_memory,
            commonR.string.sensor_description_app_memory,
            "mdi:memory",
            unitOfMeasurement = "GB",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-memory-sensor",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        val app_inactive = SensorManager.BasicSensor(
            "app_inactive",
            "binary_sensor",
            commonR.string.basic_sensor_name_app_inactive,
            commonR.string.sensor_description_app_inactive,
            "mdi:timer-outline",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-usage-sensors",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        val app_standby_bucket = SensorManager.BasicSensor(
            "app_standby_bucket",
            "sensor",
            commonR.string.basic_sensor_name_app_standby,
            commonR.string.sensor_description_app_standby,
            "mdi:android",
            deviceClass = "enum",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-usage-sensors",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        val app_importance = SensorManager.BasicSensor(
            "app_importance",
            "sensor",
            commonR.string.basic_sensor_name_app_importance,
            commonR.string.sensor_description_app_importance,
            "mdi:android",
            deviceClass = "enum",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#app-importance-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_app_sensor

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return when {
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ->
                listOf(
                    currentVersion,
                    app_rx_gb,
                    app_tx_gb,
                    app_memory,
                    app_inactive,
                    app_standby_bucket,
                    app_importance,
                )

            else -> listOf(
                currentVersion,
                app_rx_gb,
                app_tx_gb,
                app_memory,
                app_inactive,
                app_importance,
            )
        }
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate(context: Context) {
        val myUid = Process.myUid()
        updateCurrentVersion(context)
        updateAppMemory(context)
        updateAppRxGb(context, myUid)
        updateAppTxGb(context, myUid)
        updateImportanceCheck(context)
        val usageStatsManager = context.getSystemService<UsageStatsManager>()!!
        updateAppInactive(context, usageStatsManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            updateAppStandbyBucket(context, usageStatsManager)
        }
    }

    abstract fun getCurrentVersion(): String

    private suspend fun updateCurrentVersion(context: Context) {
        if (!isEnabled(context, currentVersion)) {
            return
        }

        val state = getCurrentVersion()

        onSensorUpdated(
            context,
            currentVersion,
            state,
            currentVersion.statelessIcon,
            mapOf(),
        )
    }

    private suspend fun updateAppRxGb(context: Context, appUid: Int) {
        if (!isEnabled(context, app_rx_gb)) {
            return
        }

        val appRx = try {
            TrafficStats.getUidRxBytes(appUid).toFloat() / GB
        } catch (e: Exception) {
            Timber.e(e, "Error getting app rx bytes")
            return
        }

        onSensorUpdated(
            context,
            app_rx_gb,
            appRx.toBigDecimal().setScale(4, RoundingMode.HALF_EVEN),
            app_rx_gb.statelessIcon,
            mapOf(),
        )
    }

    private suspend fun updateAppTxGb(context: Context, appUid: Int) {
        if (!isEnabled(context, app_tx_gb)) {
            return
        }

        val appTx = try {
            TrafficStats.getUidTxBytes(appUid).toFloat() / GB
        } catch (e: Exception) {
            Timber.e(e, "Error getting app tx bytes")
            return
        }

        onSensorUpdated(
            context,
            app_tx_gb,
            appTx.toBigDecimal().setScale(4, RoundingMode.HALF_EVEN),
            app_tx_gb.statelessIcon,
            mapOf(),
        )
    }

    private suspend fun updateAppMemory(context: Context) {
        if (!isEnabled(context, app_memory)) {
            return
        }

        val runTime = Runtime.getRuntime()
        val freeSize = runTime.freeMemory().toFloat() / GB
        val totalSize = runTime.totalMemory().toFloat() / GB
        val usedSize = totalSize - freeSize

        onSensorUpdated(
            context,
            app_memory,
            usedSize.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            app_memory.statelessIcon,
            mapOf(
                "free_memory" to freeSize.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
                "total_memory" to totalSize.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            ),
        )
    }

    private suspend fun updateAppInactive(context: Context, usageStatsManager: UsageStatsManager) {
        if (!isEnabled(context, app_inactive)) {
            return
        }

        val isAppInactive = usageStatsManager.isAppInactive(context.packageName)

        val icon = if (isAppInactive) "mdi:timer-off-outline" else "mdi:timer-outline"

        onSensorUpdated(
            context,
            app_inactive,
            isAppInactive,
            icon,
            mapOf(),
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun updateAppStandbyBucket(context: Context, usageStatsManager: UsageStatsManager) {
        if (!isEnabled(context, app_standby_bucket)) {
            return
        }

        val appStandbyBucket = when (usageStatsManager.appStandbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
            else -> "never"
        }

        onSensorUpdated(
            context,
            app_standby_bucket,
            appStandbyBucket,
            app_standby_bucket.statelessIcon,
            mapOf(
                "options" to listOf("active", "frequent", "rare", "restricted", "working_set", "never"),
            ),
        )
    }

    private suspend fun updateImportanceCheck(context: Context) {
        if (!isEnabled(context, app_importance)) {
            return
        }

        val appManager = context.getSystemService<ActivityManager>()!!
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

        onSensorUpdated(
            context,
            app_importance,
            importance,
            app_importance.statelessIcon,
            mapOf(
                "options" to listOf(
                    "cached", "cant_save_state", "foreground", "foreground_service", "gone",
                    "perceptible", "service", "top_sleeping", "visible", "not_running",
                ),
            ),
        )
    }
}
