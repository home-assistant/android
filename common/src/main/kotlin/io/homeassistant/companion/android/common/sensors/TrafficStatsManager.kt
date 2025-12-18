package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import java.math.RoundingMode
import kotlin.math.absoluteValue
import timber.log.Timber

class TrafficStatsManager : SensorManager {
    companion object {
        private const val GB = 1000000000

        val rxBytesMobile = SensorManager.BasicSensor(
            "mobile_rx_gb",
            "sensor",
            commonR.string.basic_sensor_name_mobile_rx_gb,
            commonR.string.sensor_description_mobile_rx_gb,
            "mdi:radio-tower",
            unitOfMeasurement = "GB",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
        val txBytesMobile = SensorManager.BasicSensor(
            "mobile_tx_gb",
            "sensor",
            commonR.string.basic_sensor_name_mobile_tx_gb,
            commonR.string.sensor_description_mobile_tx_gb,
            "mdi:radio-tower",
            unitOfMeasurement = "GB",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
        val rxBytesTotal = SensorManager.BasicSensor(
            "total_rx_gb",
            "sensor",
            commonR.string.basic_sensor_name_total_rx_gb,
            commonR.string.sensor_description_total_rx_gb,
            "mdi:radio-tower",
            unitOfMeasurement = "GB",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
        val txBytesTotal = SensorManager.BasicSensor(
            "total_tx_gb",
            "sensor",
            commonR.string.basic_sensor_name_total_tx_gb,
            commonR.string.sensor_description_total_tx_gb,
            "mdi:radio-tower",
            unitOfMeasurement = "GB",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
        private var hasCellular = false
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#traffic-stats-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_traffic_stats

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (hasCellular) {
            listOf(rxBytesMobile, txBytesMobile, rxBytesTotal, txBytesTotal)
        } else {
            listOf(rxBytesTotal, txBytesTotal)
        }
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    @Suppress("DEPRECATION") // No synchronous option to get all networks
    override fun hasSensor(context: Context): Boolean {
        val cm = context.getSystemService<ConnectivityManager>()!!
        val networkInfo = cm.allNetworks
        var networkCapabilities: NetworkCapabilities?
        for (item in networkInfo) {
            networkCapabilities = cm.getNetworkCapabilities(item)
            if (!hasCellular) {
                hasCellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
            }
        }
        return true
    }
    override suspend fun requestSensorUpdate(context: Context) {
        updateMobileRxBytes(context)
        updateMobileTxBytes(context)
        updateTotalRxBytes(context)
        updateTotalTxBytes(context)
    }

    private suspend fun updateMobileRxBytes(context: Context) {
        if (!isEnabled(context, rxBytesMobile)) {
            return
        }

        val mobileRx = try {
            TrafficStats.getMobileRxBytes().toFloat() / GB
        } catch (e: Exception) {
            Timber.e(e, "Error getting the mobile rx bytes")
            return
        }

        onSensorUpdated(
            context,
            rxBytesMobile,
            mobileRx.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            rxBytesMobile.statelessIcon,
            mapOf(),
        )
    }

    private suspend fun updateMobileTxBytes(context: Context) {
        if (!isEnabled(context, txBytesMobile)) {
            return
        }

        val mobileTx = try {
            TrafficStats.getMobileTxBytes().toFloat() / GB
        } catch (e: Exception) {
            Timber.e(e, "Error getting the mobile tx bytes")
            return
        }

        onSensorUpdated(
            context,
            txBytesMobile,
            mobileTx.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            txBytesMobile.statelessIcon,
            mapOf(),
        )
    }
    private suspend fun updateTotalRxBytes(context: Context) {
        if (!isEnabled(context, rxBytesTotal)) {
            return
        }

        val totalRx = try {
            TrafficStats.getTotalRxBytes().toFloat().absoluteValue / GB
        } catch (e: Exception) {
            Timber.e(e, "Error getting the total rx bytes")
            return
        }

        onSensorUpdated(
            context,
            rxBytesTotal,
            totalRx.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            rxBytesTotal.statelessIcon,
            mapOf(),
        )
    }

    private suspend fun updateTotalTxBytes(context: Context) {
        if (!isEnabled(context, txBytesTotal)) {
            return
        }

        val totalTx = try {
            TrafficStats.getTotalTxBytes().toFloat().absoluteValue / GB
        } catch (e: Exception) {
            Timber.e(e, "Error getting the total tx bytes")
            return
        }

        onSensorUpdated(
            context,
            txBytesTotal,
            totalTx.toBigDecimal().setScale(3, RoundingMode.HALF_EVEN),
            txBytesTotal.statelessIcon,
            mapOf(),
        )
    }
}
