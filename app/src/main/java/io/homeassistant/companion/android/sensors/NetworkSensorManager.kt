package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class NetworkSensorManager : SensorManager {
    companion object {
        private const val TAG = "NetworkSM"
        private val wifiConnection = SensorManager.BasicSensor(
            "wifi_connection",
            "sensor",
            "Wifi Connection"
        )
    }

    override val name: String
        get() = "Network Sensors"
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(wifiConnection)

    override fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            wifiConnection.id -> getWifiConnectionSensor(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getWifiConnectionSensor(context: Context): SensorRegistration<Any> {
        var conInfo: WifiInfo? = null
        var ssid = "Unknown"
        var lastScanStrength = -1
        var wifiEnabled = false

        if (checkPermission(context)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            conInfo = wifiManager.connectionInfo

            wifiEnabled = wifiManager.isWifiEnabled

            ssid = if (conInfo.networkId == -1) {
                "<not connected>"
            } else {
                conInfo.ssid.removePrefix("\"").removeSuffix("\"")
            }

            lastScanStrength = wifiManager.scanResults.firstOrNull {
                it.BSSID == conInfo.bssid
            }?.level ?: -1
        }

        var signalStrength = -1
        if (lastScanStrength != -1) {
            signalStrength = WifiManager.calculateSignalLevel(lastScanStrength, 4)
        }

        val icon = "mdi:wifi-strength-" + when (signalStrength) {
            -1 -> "off"
            0 -> "outline"
            else -> signalStrength
        }

        val attributes = conInfo?.let {
            mapOf(
                "bssid" to conInfo.bssid,
                "ip_address" to getIpAddress(conInfo.ipAddress),
                "link_speed" to conInfo.linkSpeed,
                "is_hidden" to conInfo.hiddenSSID,
                "is_wifi_on" to wifiEnabled,
                "frequency" to conInfo.frequency,
                "signal_level" to lastScanStrength
            )
        }.orEmpty()

        return wifiConnection.toSensorRegistration(
            ssid,
            icon,
            attributes
        )
    }

    private fun getIpAddress(ip: Int): String {
        return (ip and 0xFF).toString() + "." +
                (ip shr 8 and 0xFF) + "." +
                (ip shr 16 and 0xFF) + "." +
                (ip shr 24 and 0xFF)
    }
}
