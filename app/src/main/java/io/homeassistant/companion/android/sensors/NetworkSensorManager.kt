package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import io.homeassistant.companion.android.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONException
import org.json.JSONObject

class NetworkSensorManager : SensorManager {
    companion object {
        private const val TAG = "NetworkSM"
        val wifiConnection = SensorManager.BasicSensor(
            "wifi_connection",
            "sensor",
            R.string.basic_sensor_name_wifi,
            R.string.sensor_description_wifi_connection
        )
        val bssidState = SensorManager.BasicSensor(
            "wifi_bssid",
            "sensor",
            R.string.basic_sensor_name_wifi_bssid,
            R.string.sensor_description_wifi_bssid
        )
        val wifiIp = SensorManager.BasicSensor(
            "wifi_ip_address",
            "sensor",
            R.string.basic_sensor_name_wifi_ip,
            R.string.sensor_description_wifi_ip
        )
        val wifiLinkSpeed = SensorManager.BasicSensor(
            "wifi_link_speed",
            "sensor",
            R.string.basic_sensor_name_wifi_link_speed,
            R.string.sensor_description_wifi_link_speed,
            unitOfMeasurement = "Mbps"
        )
        val wifiState = SensorManager.BasicSensor(
            "wifi_state",
            "binary_sensor",
            R.string.basic_sensor_name_wifi_state,
            R.string.sensor_description_wifi_state
        )
        val wifiFrequency = SensorManager.BasicSensor(
            "wifi_frequency",
            "sensor",
            R.string.basic_sensor_name_wifi_frequency,
            R.string.sensor_description_wifi_frequency,
            unitOfMeasurement = "MHz"
        )
        val wifiSignalStrength = SensorManager.BasicSensor(
            "wifi_signal_strength",
            "sensor",
            R.string.basic_sensor_name_wifi_signal,
            R.string.sensor_description_wifi_signal,
            unitOfMeasurement = "dBm"
        )
        val publicIp = SensorManager.BasicSensor(
            "public_ip_address",
            "sensor",
            R.string.basic_sensor_name_public_ip,
            R.string.sensor_description_public_ip
        )
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_network
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(
            wifiConnection,
            bssidState,
            wifiIp,
            wifiLinkSpeed,
            wifiState,
            wifiFrequency,
            wifiSignalStrength,
            publicIp
        )

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

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateWifiConnectionSensor(context)
        updateBSSIDSensor(context)
        updateWifiIPSensor(context)
        updateWifiLinkSpeedSensor(context)
        updateWifiSensor(context)
        updateWifiFrequencySensor(context)
        updateWifiSignalStrengthSensor(context)
        updatePublicIpSensor(context)
    }

    private fun updateWifiConnectionSensor(context: Context) {
        if (!isEnabled(context, wifiConnection.id))
            return

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
                "bssid" to conInfo.bssid, // Remove after next release
                "ip_address" to getIpAddress(conInfo.ipAddress), // Remove after next release
                "link_speed" to conInfo.linkSpeed, // Remove after next release
                "is_hidden" to conInfo.hiddenSSID,
                "is_wifi_on" to wifiEnabled, // Remove after next release
                "frequency" to conInfo.frequency, // Remove after next release
                "signal_level" to lastScanStrength // Remove after next release
            )
        }.orEmpty()

        onSensorUpdated(
            context,
            wifiConnection,
            ssid,
            icon,
            attributes
        )
    }

    private fun updateBSSIDSensor(context: Context) {
        if (!isEnabled(context, bssidState.id))
            return

        var conInfo: WifiInfo? = null
        var lastScanStrength = -1

        if (checkPermission(context)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            conInfo = wifiManager.connectionInfo

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

        val bssid = if (conInfo!!.bssid == null) "<not connected>" else conInfo.bssid
        onSensorUpdated(
            context,
            bssidState,
            bssid,
            icon,
            mapOf()
        )
    }

    private fun updateWifiIPSensor(context: Context) {
        if (!isEnabled(context, wifiIp.id))
            return

        var conInfo: WifiInfo? = null
        var deviceIp = "Unknown"
        var lastScanStrength = -1

        if (checkPermission(context)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            conInfo = wifiManager.connectionInfo

            deviceIp = if (conInfo.networkId == -1) {
                "<not connected>"
            } else {
                getIpAddress(conInfo.ipAddress)
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

        onSensorUpdated(
            context,
            wifiIp,
            deviceIp,
            icon,
            mapOf()
        )
    }

    private fun updateWifiLinkSpeedSensor(context: Context) {
        if (!isEnabled(context, wifiLinkSpeed.id))
            return

        var conInfo: WifiInfo? = null
        var linkSpeed = 0
        var lastScanStrength = -1

        if (checkPermission(context)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            conInfo = wifiManager.connectionInfo

            linkSpeed = if (conInfo.networkId == -1) {
                0
            } else {
                conInfo.linkSpeed
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

        onSensorUpdated(
            context,
            wifiLinkSpeed,
            linkSpeed,
            icon,
            mapOf()
        )
    }

    private fun updateWifiSensor(context: Context) {
        if (!isEnabled(context, wifiState.id))
            return

        var wifiEnabled = false

        if (checkPermission(context)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)

            wifiEnabled = wifiManager.isWifiEnabled
        }
        val icon = if (wifiEnabled) "mdi:wifi" else "mdi:wifi-off"

        onSensorUpdated(
            context,
            wifiState,
            wifiEnabled,
            icon,
            mapOf()
        )
    }

    private fun updateWifiFrequencySensor(context: Context) {
        if (!isEnabled(context, wifiFrequency.id))
            return

        var conInfo: WifiInfo? = null
        var frequency = 0
        var lastScanStrength = -1

        if (checkPermission(context)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            conInfo = wifiManager.connectionInfo

            frequency = if (conInfo.networkId == -1) {
                0
            } else {
                conInfo.frequency
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

        onSensorUpdated(
            context,
            wifiFrequency,
            frequency,
            icon,
            mapOf()
        )
    }

    private fun updateWifiSignalStrengthSensor(context: Context) {
        if (!isEnabled(context, wifiSignalStrength.id))
            return

        var conInfo: WifiInfo? = null
        var lastScanStrength = -1

        if (checkPermission(context)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            conInfo = wifiManager.connectionInfo

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

        onSensorUpdated(
            context,
            wifiSignalStrength,
            lastScanStrength,
            icon,
            mapOf()
        )
    }

    private fun getIpAddress(ip: Int): String {
        return (ip and 0xFF).toString() + "." +
                (ip shr 8 and 0xFF) + "." +
                (ip shr 16 and 0xFF) + "." +
                (ip shr 24 and 0xFF)
    }

    private fun updatePublicIpSensor(context: Context) {
        if (!isEnabled(context, publicIp.id))
            return

        var ip = "unknown"
        val client = OkHttpClient()
        val request = Request.Builder().url("https://api.ipify.org?format=json").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Error getting response from external service", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) throw IOException("Unexpected response code $response")
                try {
                    val jsonObject = JSONObject(response.body!!.string())
                    ip = jsonObject.getString("ip")
                } catch (e: JSONException) {
                    Log.e(TAG, "Unable to parse ip address from response", e)
                }

                onSensorUpdated(
                    context,
                    publicIp,
                    ip,
                    "mdi:ip",
                    mapOf()
                )
            }
        })
    }
}
