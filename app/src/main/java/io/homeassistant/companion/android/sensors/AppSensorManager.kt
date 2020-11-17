package io.homeassistant.companion.android.sensors

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.util.Log
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
            R.string.sensor_description_current_version
        )

        val app_rx_gb = SensorManager.BasicSensor(
            "app_rx_gb",
            "sensor",
            R.string.basic_sensor_name_app_rx_gb,
            R.string.sensor_description_app_rx_gb
        )

        val app_tx_gb = SensorManager.BasicSensor(
            "app_tx_gb",
            "sensor",
            R.string.basic_sensor_name_app_tx_gb,
            R.string.sensor_description_app_tx_gb
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_app_sensor

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(currentVersion, app_rx_gb, app_tx_gb)

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        val myUid = Process.myUid()
        updateCurrentVersion(context)
        updateAppRxGb(context, myUid)
        updateAppTxGb(context, myUid)
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
}
