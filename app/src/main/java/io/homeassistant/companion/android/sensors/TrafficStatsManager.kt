package io.homeassistant.companion.android.sensors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.util.Log
import io.homeassistant.companion.android.R
import java.math.RoundingMode
import kotlin.math.absoluteValue

class TrafficStatsManager : SensorManager {
    companion object {
        private const val TAG = "TrafficStats"
        private const val GB = 1000000000

        val rxBytesMobile = SensorManager.BasicSensor(
            "mobile_rx_gb",
            "sensor",
            R.string.basic_sensor_name_mobile_rx_gb,
            R.string.sensor_description_mobile_rx_gb,
            unitOfMeasurement = "GB"
        )
        val txBytesMobile = SensorManager.BasicSensor(
            "mobile_tx_gb",
            "sensor",
            R.string.basic_sensor_name_mobile_tx_gb,
            R.string.sensor_description_mobile_tx_gb,
            unitOfMeasurement = "GB"
        )
        val rxBytesTotal = SensorManager.BasicSensor(
            "total_rx_gb",
            "sensor",
            R.string.basic_sensor_name_total_rx_gb,
            R.string.sensor_description_total_rx_gb,
            unitOfMeasurement = "GB"
        )
        val txBytesTotal = SensorManager.BasicSensor(
            "total_tx_gb",
            "sensor",
            R.string.basic_sensor_name_total_tx_gb,
            R.string.sensor_description_total_tx_gb,
            unitOfMeasurement = "GB"
        )
        private var hasCellular = false
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_traffic_stats

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = if (hasCellular) {
            listOf(rxBytesMobile, txBytesMobile, rxBytesTotal, txBytesTotal)
        } else listOf(rxBytesTotal, txBytesTotal)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(context: Context): Boolean {
        val cm: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = cm.allNetworks
        var networkCapabilities: NetworkCapabilities
        for (item in networkInfo) {
            networkCapabilities = cm.getNetworkCapabilities(item)
            if (!hasCellular)
                hasCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
        return true
    }
    override fun requestSensorUpdate(
        context: Context
    ) {
        updateMobileRxBytes(context)
        updateMobileTxBytes(context)
        updateTotalRxBytes(context)
        updateTotalTxBytes(context)
    }

    private fun updateMobileRxBytes(context: Context) {

        if (!isEnabled(context, rxBytesMobile.id))
            return

        var mobileRx = 0f
        val icon = "mdi:radio-tower"

        try {
            mobileRx = TrafficStats.getMobileRxBytes().toFloat()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the mobile rx bytes", e)
            return
        }

        mobileRx /= GB
        onSensorUpdated(context,
            rxBytesMobile,
            mobileRx.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            icon,
            mapOf()
        )
    }

    private fun updateMobileTxBytes(context: Context) {

        if (!isEnabled(context, txBytesMobile.id))
            return

        var mobileTx = 0f
        val icon = "mdi:radio-tower"

        try {
            mobileTx = TrafficStats.getMobileTxBytes().toFloat()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the mobile tx bytes", e)
            return
        }

        mobileTx /= GB
        onSensorUpdated(context,
            txBytesMobile,
            mobileTx.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            icon,
            mapOf()
        )
    }
    private fun updateTotalRxBytes(context: Context) {

        if (!isEnabled(context, rxBytesTotal.id))
            return

        var totalRx = 0f
        val icon = "mdi:radio-tower"

        try {
            totalRx = TrafficStats.getTotalRxBytes().toFloat().absoluteValue
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the total rx bytes", e)
            return
        }
        totalRx /= GB

        onSensorUpdated(context,
            rxBytesTotal,
            totalRx.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            icon,
            mapOf()
        )
    }

    private fun updateTotalTxBytes(context: Context) {

        if (!isEnabled(context, txBytesTotal.id))
            return

        var totalTx = 0f
        val icon = "mdi:radio-tower"

        try {
            totalTx = TrafficStats.getTotalTxBytes().toFloat().absoluteValue
        } catch (e: Exception) {
            Log.e(TAG, "Error getting the total tx bytes", e)
            return
        }

        totalTx /= GB

        onSensorUpdated(context,
            txBytesTotal,
            totalTx.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            icon,
            mapOf()
        )
    }
}
