package io.homeassistant.companion.android.sensor

import android.content.Context
import android.net.wifi.WifiManager
import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class NetworkSensorManager(private val context: Context) : SensorManager {

    override suspend fun getSensorRegistrations(): List<SensorRegistration<*>> {
        return listOf(SensorRegistration(getWifiConnectionSensor(), "Wifi Connection"))
    }

    override suspend fun getSensors(): List<Sensor<*>> {
        return listOf(getWifiConnectionSensor())
    }

    private fun getWifiConnectionSensor(): Sensor<Any> {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val conInfo = wifiManager.connectionInfo

        val ssid = if (conInfo.networkId == -1) {
            "<not connected>"
        } else {
            conInfo.ssid.removePrefix("\"").removeSuffix("\"")
        }

        val lastScanStrength = wifiManager.scanResults.firstOrNull {
            it.BSSID == conInfo.bssid
        }?.level ?: -1

        var signalStrength = -1
        if (lastScanStrength != -1) {
            signalStrength = WifiManager.calculateSignalLevel(lastScanStrength, 4)
        }

        val icon = "mdi:wifi-strength-" + when (signalStrength) {
            -1 -> "off"
            0 -> "outline"
            else -> signalStrength
        }

        return Sensor(
            "wifi_connection",
            ssid,
            "sensor",
            icon,
            mapOf(
                "bssid" to conInfo.bssid,
                "ip_address" to getIpAddress(conInfo.ipAddress),
                "link_speed" to conInfo.linkSpeed,
                "is_hidden" to conInfo.hiddenSSID,
                "frequency" to conInfo.frequency,
                "signal_level" to lastScanStrength
            )
        )
    }

    private fun getIpAddress(ip: Int): String {
        return (ip and 0xFF).toString() + "." +
                (ip shr 8 and 0xFF) + "." +
                (ip shr 16 and 0xFF) + "." +
                (ip shr 24 and 0xFF)
    }
}
