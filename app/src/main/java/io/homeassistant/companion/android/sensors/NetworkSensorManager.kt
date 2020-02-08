package io.homeassistant.companion.android.sensors

import android.content.Context
import android.net.wifi.WifiManager
import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class NetworkSensorManager : SensorManager {
    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        val sensorRegistrations = mutableListOf<SensorRegistration<Any>>()

        sensorRegistrations.add(
            SensorRegistration(
                getWifiConnectionSensor(context),
                "Wifi Connection"
            )
        )

        return sensorRegistrations
    }

    override fun getSensors(context: Context): List<Sensor<Any>> {
        val sensors = mutableListOf<Sensor<Any>>()

        sensors.add(getWifiConnectionSensor(context))

        return sensors
    }

    private fun getWifiConnectionSensor(context: Context): Sensor<Any> {
        val wifiManager =
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        val conInfo = wifiManager.connectionInfo
        return Sensor(
            "wifi_connection",
            conInfo.ssid,
            "sensor",
            "mdi:wifi-strength-4",
            mapOf(
                "bssid" to conInfo.bssid,
                "ip" to getIpAddress(conInfo.ipAddress),
                "link_speed" to conInfo.linkSpeed,
                "is_hidden" to conInfo.hiddenSSID,
                "frequency" to conInfo.frequency
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
