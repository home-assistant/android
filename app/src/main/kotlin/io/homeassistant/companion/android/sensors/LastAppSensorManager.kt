package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.isAutomotive
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class LastAppSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        @ProvidesSensor
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

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(last_used)
    }

    override fun hasSensor(): Boolean {
        return !applicationContext.isAutomotive()
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.PACKAGE_USAGE_STATS)
    }

    override suspend fun requestSensorUpdate() {
        updateLastApp(applicationContext)
    }

    private suspend fun updateLastApp(applicationContext: Context) {
        if (!isEnabled(last_used)) {
            return
        }

        val usageStats = applicationContext.getSystemService<UsageStatsManager>()!!
        val current = System.currentTimeMillis()
        val lastApp =
            usageStats.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, current - 1000 * 1000, current).maxByOrNull {
                it.lastTimeUsed
            }?.packageName
                ?: "none"

        var appLabel = STATE_UNKNOWN

        try {
            val pm = applicationContext.packageManager
            val appInfo = if (SdkVersion.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
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
            last_used,
            lastApp,
            last_used.statelessIcon,
            mapOf(
                "Label" to appLabel,
            ),
        )
    }
}
