package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
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
            "mdi:wifi",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val bssidState = SensorManager.BasicSensor(
            "wifi_bssid",
            "sensor",
            commonR.string.basic_sensor_name_wifi_bssid,
            commonR.string.sensor_description_wifi_bssid,
            "mdi:wifi",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val wifiIp = SensorManager.BasicSensor(
            "wifi_ip_address",
            "sensor",
            commonR.string.basic_sensor_name_wifi_ip,
            commonR.string.sensor_description_wifi_ip,
            "mdi:ip",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val wifiLinkSpeed = SensorManager.BasicSensor(
            "wifi_link_speed",
            "sensor",
            commonR.string.basic_sensor_name_wifi_link_speed,
            commonR.string.sensor_description_wifi_link_speed,
            "mdi:wifi-strength-3",
            unitOfMeasurement = "Mbps",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val wifiState = SensorManager.BasicSensor(
            "wifi_state",
            "binary_sensor",
            commonR.string.basic_sensor_name_wifi_state,
            commonR.string.sensor_description_wifi_state,
            "mdi:wifi",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val wifiFrequency = SensorManager.BasicSensor(
            "wifi_frequency",
            "sensor",
            commonR.string.basic_sensor_name_wifi_frequency,
            commonR.string.sensor_description_wifi_frequency,
            "mdi:wifi",
            unitOfMeasurement = "MHz",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val wifiSignalStrength = SensorManager.BasicSensor(
            "wifi_signal_strength",
            "sensor",
            commonR.string.basic_sensor_name_wifi_signal,
            commonR.string.sensor_description_wifi_signal,
            "mdi:wifi-strength-3",
            deviceClass = "signal_strength",
            unitOfMeasurement = "dBm",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        val publicIp = SensorManager.BasicSensor(
            "public_ip_address",
            "sensor",
            commonR.string.basic_sensor_name_public_ip,
            commonR.string.sensor_description_public_ip,
            "mdi:ip",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#public-ip-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val networkType = SensorManager.BasicSensor(
            "network_type",
            "sensor",
            commonR.string.basic_sensor_name_network_type,
            commonR.string.sensor_description_network_type,
            "mdi:network",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#network-type-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
        private const val SETTING_GET_CURRENT_BSSID = "network_get_current_bssid"
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#connection-type-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_network
    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        val wifiSensors = listOf(
            wifiConnection,
            bssidState,
            wifiIp,
            wifiLinkSpeed,
            wifiState,
            wifiFrequency,
            wifiSignalStrength
        )
        val list = if (hasWifi(context)) {
            wifiSensors.plus(publicIp)
        } else {
            listOf(publicIp)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            list.plus(networkType)
        } else {
            list
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            sensorId == publicIp.id || sensorId == networkType.id -> {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            updateNetworkType(context)
        }
    }

    private fun hasWifi(context: Context): Boolean =
        context.applicationContext.getSystemService<WifiManager>() != null

    private fun updateWifiConnectionSensor(context: Context) {
        if (!isEnabled(context, wifiConnection) || !hasWifi(context)) {
            return
        }

        var conInfo: WifiInfo? = null
        var ssid = "Unknown"
        var connected = false

        if (checkPermission(context, wifiConnection.id)) {
            val wifiManager =
                context.applicationContext.getSystemService<WifiManager>()!!
            conInfo = wifiManager.connectionInfo

            if (conInfo.networkId == -1) {
                if (conInfo.linkSpeed == -1) {
                    ssid = "<not connected>"
                } else {
                    ssid = "<unknown>"
                    connected = true
                }
            } else {
                ssid = conInfo.ssid.removePrefix("\"").removeSuffix("\"")
                connected = true
            }
        }

        val icon = if (connected) "mdi:wifi" else "mdi:wifi-off"

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
        if (!isEnabled(context, bssidState) || !hasWifi(context)) {
            return
        }

        var conInfo: WifiInfo? = null

        if (checkPermission(context, bssidState.id)) {
            val wifiManager =
                context.applicationContext.getSystemService<WifiManager>()!!
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
                sensorDao.add(SensorSetting(bssidState.id, SETTING_GET_CURRENT_BSSID, "false", SensorSettingType.TOGGLE))
                sensorDao.add(SensorSetting(bssidState.id, settingName, bssid, SensorSettingType.STRING))
            }
        } else {
            if (currentSetting != "") {
                bssid = currentSetting
            } else {
                sensorDao.removeSetting(bssidState.id, settingName)
            }

            sensorDao.add(SensorSetting(bssidState.id, SETTING_GET_CURRENT_BSSID, "false", SensorSettingType.TOGGLE))
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
        if (!isEnabled(context, wifiIp) || !hasWifi(context)) {
            return
        }

        var deviceIp = "Unknown"

        if (checkPermission(context, wifiIp.id)) {
            val wifiManager =
                context.applicationContext.getSystemService<WifiManager>()!!
            val conInfo = wifiManager.connectionInfo

            deviceIp = if (conInfo.networkId == -1 && conInfo.linkSpeed == -1) {
                "<not connected>"
            } else {
                getIpAddress(conInfo.ipAddress)
            }
        }

        onSensorUpdated(
            context,
            wifiIp,
            deviceIp,
            wifiIp.statelessIcon,
            mapOf()
        )
    }

    private fun updateWifiLinkSpeedSensor(context: Context) {
        if (!isEnabled(context, wifiLinkSpeed) || !hasWifi(context)) {
            return
        }

        var linkSpeed = 0
        var rssi = -1

        if (checkPermission(context, wifiLinkSpeed.id)) {
            val wifiManager =
                context.applicationContext.getSystemService<WifiManager>()!!
            val conInfo = wifiManager.connectionInfo

            linkSpeed = if (conInfo.linkSpeed == -1) {
                0
            } else {
                conInfo.linkSpeed
            }

            if (conInfo.networkId != -1 || conInfo.linkSpeed != -1) {
                rssi = conInfo.rssi
            }
        }

        var signalStrength = -1
        if (rssi != -1) {
            signalStrength = WifiManager.calculateSignalLevel(rssi, 4)
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
        if (!isEnabled(context, wifiState) || !hasWifi(context)) {
            return
        }

        var wifiEnabled = false

        if (checkPermission(context, wifiState.id)) {
            val wifiManager =
                context.applicationContext.getSystemService<WifiManager>()!!

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
        if (!isEnabled(context, wifiFrequency) || !hasWifi(context)) {
            return
        }

        var frequency = 0

        if (checkPermission(context, wifiFrequency.id)) {
            val wifiManager =
                context.applicationContext.getSystemService<WifiManager>()!!
            val conInfo = wifiManager.connectionInfo

            frequency = if (conInfo.networkId == -1 && conInfo.linkSpeed == -1) {
                0
            } else {
                conInfo.frequency
            }
        }

        onSensorUpdated(
            context,
            wifiFrequency,
            frequency,
            wifiFrequency.statelessIcon,
            mapOf()
        )
    }

    private fun updateWifiSignalStrengthSensor(context: Context) {
        if (!isEnabled(context, wifiSignalStrength) || !hasWifi(context)) {
            return
        }

        var rssi = -1

        if (checkPermission(context, wifiSignalStrength.id)) {
            val wifiManager =
                context.applicationContext.getSystemService<WifiManager>()!!
            val conInfo = wifiManager.connectionInfo

            if (conInfo.networkId != -1 || conInfo.linkSpeed != -1) {
                rssi = conInfo.rssi
            }
        }

        var signalStrength = -1
        if (rssi != -1) {
            signalStrength = WifiManager.calculateSignalLevel(rssi, 4)
        }

        val icon = "mdi:wifi-strength-" + when (signalStrength) {
            -1 -> "off"
            0 -> "outline"
            else -> signalStrength
        }

        onSensorUpdated(
            context,
            wifiSignalStrength,
            rssi,
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
        if (!isEnabled(context, publicIp)) {
            return
        }

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
                    publicIp.statelessIcon,
                    mapOf()
                )
            }
        })
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.M)
    private fun updateNetworkType(context: Context) {
        if (!isEnabled(context, networkType)) {
            return
        }

        val connectivityManager = context.getSystemService<ConnectivityManager>()
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

        var networkCapability = "unavailable"
        var metered = false
        if (capabilities != null) {
            networkCapability =
                when {
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) -> "bluetooth"
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) -> "cellular"
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) -> "ethernet"
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) -> "lowpan"
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB)) -> "usb"
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) -> "vpn"
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) -> "wifi"
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) -> "wifi_aware"
                    else -> "unknown"
                }

            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }

        val icon = when (networkCapability) {
            "bluetooth" -> "mdi:bluetooth"
            "cellular" -> "mdi:signal-cellular-3"
            "ethernet" -> "mdi:ethernet"
            "usb" -> "mdi:usb"
            "wifi", "wifi_aware" -> "mdi:wifi"
            else -> "mdi:network"
        }

        onSensorUpdated(
            context,
            networkType,
            networkCapability,
            icon,
            mapOf(
                "metered" to metered
            )
        )
    }
}
