package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Setting
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONException
import org.json.JSONObject
import io.homeassistant.companion.android.common.R as commonR

class NetworkSensorManager : SensorManager {
    companion object {
        private const val TAG = "NetworkSM"
        val wifiConnection = SensorManager.BasicSensor(
            "wifi_connection",
            "sensor",
            commonR.string.basic_sensor_name_wifi,
            commonR.string.sensor_description_wifi_connection,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val bssidState = SensorManager.BasicSensor(
            "wifi_bssid",
            "sensor",
            commonR.string.basic_sensor_name_wifi_bssid,
            commonR.string.sensor_description_wifi_bssid,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val wifiIp = SensorManager.BasicSensor(
            "wifi_ip_address",
            "sensor",
            commonR.string.basic_sensor_name_wifi_ip,
            commonR.string.sensor_description_wifi_ip,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val wifiLinkSpeed = SensorManager.BasicSensor(
            "wifi_link_speed",
            "sensor",
            commonR.string.basic_sensor_name_wifi_link_speed,
            commonR.string.sensor_description_wifi_link_speed,
            unitOfMeasurement = "Mbps",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val wifiState = SensorManager.BasicSensor(
            "wifi_state",
            "binary_sensor",
            commonR.string.basic_sensor_name_wifi_state,
            commonR.string.sensor_description_wifi_state,
            entityCategory = SensorManager.ENTITY_CATEGORY_CONFIG
        )
        val wifiFrequency = SensorManager.BasicSensor(
            "wifi_frequency",
            "sensor",
            commonR.string.basic_sensor_name_wifi_frequency,
            commonR.string.sensor_description_wifi_frequency,
            unitOfMeasurement = "MHz",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val wifiSignalStrength = SensorManager.BasicSensor(
            "wifi_signal_strength",
            "sensor",
            commonR.string.basic_sensor_name_wifi_signal,
            commonR.string.sensor_description_wifi_signal,
            "signal_strength",
            unitOfMeasurement = "dBm",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val publicIp = SensorManager.BasicSensor(
            "public_ip_address",
            "sensor",
            commonR.string.basic_sensor_name_public_ip,
            commonR.string.sensor_description_public_ip,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#public-ip-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private const val SETTING_GET_CURRENT_BSSID = "network_get_current_bssid"
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#connection-type-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_network
    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(
            wifiConnection,
            bssidState,
            wifiIp,
            wifiLinkSpeed,
            wifiState,
            wifiFrequency,
            wifiSignalStrength,
            publicIp
        )
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            sensorId == publicIp.id -> {
                arrayOf()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
            else -> {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
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

        if (checkPermission(context, wifiConnection.id)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            conInfo = wifiManager.connectionInfo

            ssid = if (conInfo.networkId == -1) {
                "<not connected>"
            } else {
                conInfo.ssid.removePrefix("\"").removeSuffix("\"")
            }
        }

        val icon = if (ssid != "<not connected>") "mdi:wifi" else "mdi:wifi-off"

        val attributes = conInfo?.let {
            mapOf("is_hidden" to conInfo.hiddenSSID)
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

        if (checkPermission(context, bssidState.id)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            conInfo = wifiManager.connectionInfo
        }

        var bssid = if (conInfo!!.bssid == null) "<not connected>" else conInfo.bssid

        val settingName = "network_replace_mac_var1:$bssid:"
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val sensorSettings = sensorDao.getSettings(bssidState.id)
        val getCurrentBSSID = sensorSettings.firstOrNull { it.name == SETTING_GET_CURRENT_BSSID }?.value ?: "false"
        val currentSetting = sensorSettings.firstOrNull { it.name == settingName }?.value ?: ""
        if (getCurrentBSSID == "true") {
            if (currentSetting == "") {
                sensorDao.add(Setting(bssidState.id, SETTING_GET_CURRENT_BSSID, "false", "toggle"))
                sensorDao.add(Setting(bssidState.id, settingName, bssid, "string"))
            }
        } else {
            if (currentSetting != "")
                bssid = currentSetting
            else
                sensorDao.removeSetting(bssidState.id, settingName)

            sensorDao.add(Setting(bssidState.id, SETTING_GET_CURRENT_BSSID, "false", "toggle"))
        }

        val icon = if (bssid != "<not connected>") "mdi:wifi" else "mdi:wifi-off"
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

        var deviceIp = "Unknown"

        if (checkPermission(context, wifiIp.id)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            val conInfo = wifiManager.connectionInfo

            deviceIp = if (conInfo.networkId == -1) {
                "<not connected>"
            } else {
                getIpAddress(conInfo.ipAddress)
            }
        }

        val icon = "mdi:ip"

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

        var linkSpeed = 0
        var lastScanStrength = -1

        if (checkPermission(context, wifiLinkSpeed.id)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            val conInfo = wifiManager.connectionInfo

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

        if (checkPermission(context, wifiState.id)) {
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

        var frequency = 0

        if (checkPermission(context, wifiFrequency.id)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            val conInfo = wifiManager.connectionInfo

            frequency = if (conInfo.networkId == -1) {
                0
            } else {
                conInfo.frequency
            }
        }

        val icon = "mdi:wifi"

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

        var lastScanStrength = -1

        if (checkPermission(context, wifiSignalStrength.id)) {
            val wifiManager =
                (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            val conInfo = wifiManager.connectionInfo

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
