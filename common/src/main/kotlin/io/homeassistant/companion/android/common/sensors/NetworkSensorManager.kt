package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.getStringOrElse
import io.homeassistant.companion.android.common.util.toJsonObjectOrNull
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import java.lang.reflect.Method
import java.net.Inet6Address
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import timber.log.Timber

class NetworkSensorManager : SensorManager {
    companion object {
        val hotspotState = SensorManager.BasicSensor(
            "hotspot_state",
            "binary_sensor",
            commonR.string.basic_sensor_name_hotspot_state,
            commonR.string.sensor_description_hotspot,
            "mdi:access-point",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val wifiConnection = SensorManager.BasicSensor(
            "wifi_connection",
            "sensor",
            commonR.string.basic_sensor_name_wifi,
            commonR.string.sensor_description_wifi_connection,
            "mdi:wifi",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val bssidState = SensorManager.BasicSensor(
            "wifi_bssid",
            "sensor",
            commonR.string.basic_sensor_name_wifi_bssid,
            commonR.string.sensor_description_wifi_bssid,
            "mdi:wifi",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val wifiIp = SensorManager.BasicSensor(
            "wifi_ip_address",
            "sensor",
            commonR.string.basic_sensor_name_wifi_ip,
            commonR.string.sensor_description_wifi_ip,
            "mdi:ip",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
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
        )
        val wifiState = SensorManager.BasicSensor(
            "wifi_state",
            "binary_sensor",
            commonR.string.basic_sensor_name_wifi_state,
            commonR.string.sensor_description_wifi_state,
            "mdi:wifi",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
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
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
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
        )
        val publicIp = SensorManager.BasicSensor(
            "public_ip_address",
            "sensor",
            commonR.string.basic_sensor_name_public_ip,
            commonR.string.sensor_description_public_ip,
            "mdi:ip",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#public-ip-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
        val ip6Addresses = SensorManager.BasicSensor(
            "ip6_addresses",
            "sensor",
            commonR.string.basic_sensor_name_ip6_addresses,
            commonR.string.sensor_description_ip6_addresses,
            "mdi:ip",
            unitOfMeasurement = "address(es)",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
        val networkType = SensorManager.BasicSensor(
            "network_type",
            "sensor",
            commonR.string.basic_sensor_name_network_type,
            commonR.string.sensor_description_network_type,
            "mdi:network",
            deviceClass = "enum",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#network-type-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
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
            wifiSignalStrength,
        )
        val list = if (hasWifi(context)) {
            val withPublicIp = wifiSensors + publicIp
            if (hasHotspot(context)) {
                withPublicIp + hotspotState
            } else {
                withPublicIp
            }
        } else {
            listOf(publicIp)
        }
        return list + networkType + ip6Addresses
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return when {
            sensorId == hotspotState.id || sensorId == publicIp.id || sensorId == networkType.id -> {
                arrayOf()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                )
            }

            else -> {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    override suspend fun requestSensorUpdate(context: Context) {
        updateHotspotEnabledSensor(context)
        updateWifiConnectionSensor(context)
        updateBSSIDSensor(context)
        updateWifiIPSensor(context)
        updateWifiLinkSpeedSensor(context)
        updateWifiSensor(context)
        updateWifiFrequencySensor(context)
        updateWifiSignalStrengthSensor(context)
        updatePublicIpSensor(context)
        updateNetworkType(context)
        updateIP6Sensor(context)
    }

    private fun hasWifi(context: Context): Boolean = context.applicationContext.getSystemService<WifiManager>() != null

    @SuppressLint("PrivateApi")
    private fun hasHotspot(context: Context): Boolean {
        // Watch doesn't have hotspot.
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return false
        }
        val wifiManager: WifiManager = context.applicationContext.getSystemService()!!
        return try {
            wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            true
        } catch (e: NoSuchMethodException) {
            false
        }
    }
    private suspend fun updateHotspotEnabledSensor(context: Context) {
        if (!isEnabled(context, hotspotState)) {
            return
        }
        val wifiManager: WifiManager = context.getSystemService()!!

        @SuppressLint("PrivateApi")
        val method: Method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
        method.isAccessible = true
        val enabled = method.invoke(wifiManager) as Boolean
        val icon = if (enabled) "mdi:access-point" else "mdi:access-point-off"
        onSensorUpdated(
            context,
            hotspotState,
            enabled,
            icon,
            mapOf(),
        )
    }
    private suspend fun updateWifiConnectionSensor(context: Context) {
        if (!isEnabled(context, wifiConnection) || !hasWifi(context)) {
            return
        }

        var conInfo: WifiInfo? = null
        var ssid = STATE_UNKNOWN
        var connected = false

        if (checkPermission(context, wifiConnection.id)) {
            @Suppress("DEPRECATION") // Unable to get SSID info (instantly) using callback
            conInfo = context.getSystemService<WifiManager>()?.connectionInfo

            if (conInfo == null || conInfo.networkId == -1) {
                if (conInfo == null || conInfo.linkSpeed == -1) {
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
            attributes,
        )
    }

    private suspend fun updateBSSIDSensor(context: Context) {
        if (!isEnabled(context, bssidState) || !hasWifi(context)) {
            return
        }

        var conInfo: WifiInfo? = null

        if (checkPermission(context, bssidState.id)) {
            @Suppress("DEPRECATION") // Unable to get BSSID info (instantly) using callback
            conInfo = context.getSystemService<WifiManager>()?.connectionInfo
        }

        var bssid = if (conInfo?.bssid == null) "<not connected>" else conInfo.bssid

        val settingName = "network_replace_mac_var1:$bssid:"
        val sensorDao = AppDatabase.getInstance(context).sensorDao()
        val sensorSettings = sensorDao.getSettings(bssidState.id)
        val getCurrentBSSID = sensorSettings.firstOrNull { it.name == SETTING_GET_CURRENT_BSSID }?.value ?: "false"
        val currentSetting = sensorSettings.firstOrNull { it.name == settingName }?.value ?: ""
        if (getCurrentBSSID == "true") {
            if (currentSetting == "") {
                sensorDao.add(
                    SensorSetting(bssidState.id, SETTING_GET_CURRENT_BSSID, "false", SensorSettingType.TOGGLE),
                )
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
            mapOf(),
        )
    }

    private suspend fun updateWifiIPSensor(context: Context) {
        if (!isEnabled(context, wifiIp) || !hasWifi(context)) {
            return
        }

        var deviceIp = STATE_UNKNOWN

        if (checkPermission(context, wifiIp.id)) {
            val conInfo = getWifiConnectionInfo(context)

            deviceIp = if (conInfo == null || (conInfo.networkId == -1 && conInfo.linkSpeed == -1)) {
                "<not connected>"
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val connectivityManager = context.applicationContext.getSystemService<ConnectivityManager>()
                    connectivityManager?.activeNetwork?.let {
                        // Get the IPv4 address without prefix length
                        connectivityManager.getLinkProperties(it)?.linkAddresses
                            ?.firstOrNull { address -> !address.toString().contains(":") }
                            ?.toString()?.split("/")?.get(0)
                    } ?: ""
                } else {
                    @Suppress("DEPRECATION")
                    getIpAddress(conInfo.ipAddress)
                }
            }
        }

        onSensorUpdated(
            context,
            wifiIp,
            deviceIp,
            wifiIp.statelessIcon,
            mapOf(),
        )
    }

    private suspend fun updateIP6Sensor(context: Context) {
        if (!isEnabled(context, ip6Addresses)) {
            return
        }
        var ipAddressList: List<String> = ArrayList()
        var totalAddresses = 0

        if (checkPermission(context, ip6Addresses.id)) {
            val connectivityManager = context.applicationContext.getSystemService<ConnectivityManager>()
            val activeNetwork = connectivityManager?.activeNetwork
            val ipAddresses = connectivityManager?.getLinkProperties(activeNetwork)?.linkAddresses
            if (!ipAddresses.isNullOrEmpty()) {
                val ip6Addresses = ipAddresses.filter { linkAddress -> linkAddress.address is Inet6Address }
                if (ip6Addresses.isNotEmpty()) {
                    ipAddressList =
                        ipAddressList.plus(elements = ip6Addresses.map { linkAddress -> linkAddress.toString() })
                    totalAddresses += ip6Addresses.size
                }
            }
        }
        onSensorUpdated(
            context,
            ip6Addresses,
            totalAddresses,
            ip6Addresses.statelessIcon,
            mapOf(
                "ip6_addresses" to ipAddressList,
            ),
        )
    }

    private suspend fun updateWifiLinkSpeedSensor(context: Context) {
        if (!isEnabled(context, wifiLinkSpeed) || !hasWifi(context)) {
            return
        }

        var linkSpeed = 0
        var rssi = -1

        if (checkPermission(context, wifiLinkSpeed.id)) {
            val conInfo = getWifiConnectionInfo(context)

            linkSpeed = if (conInfo == null || conInfo.linkSpeed == -1) {
                0
            } else {
                conInfo.linkSpeed
            }

            if (conInfo != null && (conInfo.networkId != -1 || conInfo.linkSpeed != -1)) {
                rssi = conInfo.rssi
            }
        }

        var signalStrength = -1
        if (rssi != -1) {
            @Suppress("DEPRECATION") // Always use 4 levels instead of depending on device
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
            mapOf(),
        )
    }

    private suspend fun updateWifiSensor(context: Context) {
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
            mapOf(),
        )
    }

    private suspend fun updateWifiFrequencySensor(context: Context) {
        if (!isEnabled(context, wifiFrequency) || !hasWifi(context)) {
            return
        }

        var frequency = 0

        if (checkPermission(context, wifiFrequency.id)) {
            val conInfo = getWifiConnectionInfo(context)

            frequency = if (conInfo == null || (conInfo.networkId == -1 && conInfo.linkSpeed == -1)) {
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
            mapOf(),
        )
    }

    private suspend fun updateWifiSignalStrengthSensor(context: Context) {
        if (!isEnabled(context, wifiSignalStrength) || !hasWifi(context)) {
            return
        }

        var rssi = -1

        if (checkPermission(context, wifiSignalStrength.id)) {
            val conInfo = getWifiConnectionInfo(context)

            if (conInfo != null && (conInfo.networkId != -1 || conInfo.linkSpeed != -1)) {
                rssi = conInfo.rssi
            }
        }

        var signalStrength = -1
        if (rssi != -1) {
            @Suppress("DEPRECATION") // Always use 4 levels instead of depending on device
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
            mapOf(),
        )
    }

    private fun getIpAddress(ip: Int): String {
        return (ip and 0xFF).toString() + "." +
            (ip shr 8 and 0xFF) + "." +
            (ip shr 16 and 0xFF) + "." +
            (ip shr 24 and 0xFF)
    }

    private suspend fun updatePublicIpSensor(context: Context) {
        if (!isEnabled(context, publicIp)) {
            return
        }

        var ip = STATE_UNKNOWN
        val client = OkHttpClient()
        val request = Request.Builder().url("https://api.ipify.org?format=json").build()

        suspendCancellableCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Timber.e(e, "Error getting response from external service")
                    continuation.resume(Unit) { cause, _, _ ->
                        // no-op
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) throw IOException("Unexpected response code $response")
                    try {
                        val jsonObject = response.body.string().toJsonObjectOrNull()
                        ip = jsonObject?.getStringOrElse("ip", "") ?: ""
                    } catch (e: SerializationException) {
                        Timber.e(e, "Unable to parse ip address from response")
                    }

                    continuation.resume(Unit) { cause, _, _ ->
                        // no-op
                    }
                }
            })
        }
        onSensorUpdated(
            context,
            publicIp,
            ip,
            publicIp.statelessIcon,
            mapOf(),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun updateNetworkType(context: Context) {
        if (!isEnabled(context, networkType)) {
            return
        }

        val connectivityManager = context.getSystemService<ConnectivityManager>()
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

        var networkCapability = STATE_UNAVAILABLE
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
                    else -> STATE_UNKNOWN
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
                "metered" to metered,
                "options" to listOf("bluetooth", "cellular", "ethernet", "lowpan", "usb", "vpn", "wifi", "wifi_aware"),
            ),
        )
    }

    /** Get WiFi connection info (without location data such as (B)SSID on Android >=S) */
    private fun getWifiConnectionInfo(context: Context): WifiInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connectivityManager = context.applicationContext.getSystemService<ConnectivityManager>()
            connectivityManager?.activeNetwork?.let {
                val info = connectivityManager.getNetworkCapabilities(it)?.transportInfo

                // If WifiInfo is null default to the deprecated method as a fix for some devices that may return null
                @Suppress("DEPRECATION")
                return@let info as? WifiInfo
                    ?: context.applicationContext.getSystemService<WifiManager>()?.connectionInfo
            }
        } else {
            @Suppress("DEPRECATION")
            context.applicationContext.getSystemService<WifiManager>()?.connectionInfo
        }
}
