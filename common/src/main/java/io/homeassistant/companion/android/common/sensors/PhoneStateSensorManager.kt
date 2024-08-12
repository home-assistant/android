package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellSignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN

class PhoneStateSensorManager : SensorManager {

    companion object {
        private const val TAG = "PhoneStateSM"
        val phoneState = SensorManager.BasicSensor(
            "phone_state",
            "sensor",
            commonR.string.basic_sensor_name_phone,
            commonR.string.sensor_description_phone_state,
            "mdi:phone",
            deviceClass = "enum",
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#phone-state-sensor",
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )

        val sim_1 = SensorManager.BasicSensor(
            "sim_1",
            "sensor",
            commonR.string.basic_sensor_name_sim1,
            commonR.string.sensor_description_sim_1,
            "mdi:sim",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )

        val sim_2 = SensorManager.BasicSensor(
            "sim_2",
            "sensor",
            commonR.string.basic_sensor_name_sim2,
            commonR.string.sensor_description_sim_2,
            "mdi:sim",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )

        val signalStrength = SensorManager.BasicSensor(
            "signal_strength",
            "sensor",
            commonR.string.basic_sensor_name_signal_strength,
            commonR.string.sensor_description_signal_strength,
            "mdi:signal",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val dataNetworkType = SensorManager.BasicSensor(
            "data_network_type",
            "sensor",
            commonR.string.basic_sensor_name_data_network_type,
            commonR.string.sensor_description_data_network_type,
            "mdi:signal",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
    }

    @SuppressLint("InlinedApi")
    @Suppress("DEPRECATION")
    private val dataTypeMap = mapOf(
        TelephonyManager.NETWORK_TYPE_GPRS to "GPRS",
        TelephonyManager.NETWORK_TYPE_EDGE to "EDGE",
        TelephonyManager.NETWORK_TYPE_UMTS to "UMTS",
        TelephonyManager.NETWORK_TYPE_CDMA to "CDMA",
        TelephonyManager.NETWORK_TYPE_EVDO_0 to "EVDO revision 0",
        TelephonyManager.NETWORK_TYPE_EVDO_A to "EVDO revision A",
        TelephonyManager.NETWORK_TYPE_1xRTT to "1xRTT",
        TelephonyManager.NETWORK_TYPE_HSDPA to "HSDPA",
        TelephonyManager.NETWORK_TYPE_HSUPA to "HSUPA",
        TelephonyManager.NETWORK_TYPE_HSPA to "HSPA",
        TelephonyManager.NETWORK_TYPE_IDEN to "iDen",
        TelephonyManager.NETWORK_TYPE_EVDO_B to "EVDO revision B",
        TelephonyManager.NETWORK_TYPE_LTE to "LTE",
        TelephonyManager.NETWORK_TYPE_EHRPD to "eHRPD",
        TelephonyManager.NETWORK_TYPE_HSPAP to "HSPA+",
        TelephonyManager.NETWORK_TYPE_GSM to "GSM",
        TelephonyManager.NETWORK_TYPE_TD_SCDMA to "TD_SCDMA",
        TelephonyManager.NETWORK_TYPE_IWLAN to "IWLAN",
        TelephonyManager.NETWORK_TYPE_NR to "NR (New Radio) 5G",
        TelephonyManager.NETWORK_TYPE_UNKNOWN to STATE_UNKNOWN
    )

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#cellular-provider-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_phone
    override fun hasSensor(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }
    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return when {
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)) ->
                listOf(phoneState, sim_1, sim_2, signalStrength, dataNetworkType)
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)) ->
                listOf(phoneState, sim_1, sim_2, dataNetworkType)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 -> {
                listOf(phoneState, sim_1, sim_2)
            }
            else -> {
                listOf(phoneState)
            }
        }
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.READ_PHONE_STATE)
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        checkPhoneState(context)
        updateSimSensor(context, 0)
        updateSimSensor(context, 1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            updateSignalStrength(context)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateDataNetworkType(context)
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkPhoneState(context: Context) {
        if (isEnabled(context, phoneState)) {
            var currentPhoneState = STATE_UNKNOWN

            if (checkPermission(context, phoneState.id)) {
                val telephonyManager =
                    context.applicationContext.getSystemService<TelephonyManager>()!!

                // Deprecated function provides state for any call, not for a specific subscription only
                @Suppress("DEPRECATION")
                currentPhoneState = when (telephonyManager.callState) {
                    TelephonyManager.CALL_STATE_IDLE -> "idle"
                    TelephonyManager.CALL_STATE_RINGING -> "ringing"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
                    else -> STATE_UNKNOWN
                }
            }

            updatePhoneStateSensor(context, currentPhoneState)
        }
    }

    private fun updatePhoneStateSensor(context: Context, state: String) {
        var phoneIcon = "mdi:phone"
        if (state == "ringing" || state == "offhook") {
            phoneIcon += "-in-talk"
        }

        onSensorUpdated(
            context,
            phoneState,
            state,
            phoneIcon,
            mapOf(
                "options" to listOf("idle", "ringing", "offhook")
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun updateSimSensor(context: Context, slotIndex: Int) {
        val basicSimSensor = when (slotIndex) {
            0 -> sim_1
            1 -> sim_2
            else -> throw IllegalArgumentException("Invalid sim slot: $slotIndex")
        }
        if (!isEnabled(context, basicSimSensor)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            var displayName = STATE_UNAVAILABLE
            val attrs = mutableMapOf<String, Any>()

            if (checkPermission(context, basicSimSensor.id)) {
                val subscriptionManager =
                    context.applicationContext.getSystemService<SubscriptionManager>()
                val info: SubscriptionInfo? =
                    subscriptionManager?.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)

                if (info != null) {
                    try {
                        displayName = info.displayName?.toString() ?: info.carrierName.toString()
                        attrs["carrier name"] = info.carrierName
                        attrs["iso country code"] = info.countryIso
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            attrs["carrier id"] = info.carrierId
                            attrs["mcc"] = info.mccString.toString()
                            attrs["mnc"] = info.mncString.toString()
                            attrs["is opportunistic"] = info.isOpportunistic
                            attrs["data roaming"] = if (info.dataRoaming == SubscriptionManager.DATA_ROAMING_ENABLE) "enable" else "disable"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Unable to get SIM data", e)
                    }
                }
            }

            onSensorUpdated(
                context,
                basicSimSensor,
                displayName,
                basicSimSensor.statelessIcon,
                attrs
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateSignalStrength(context: Context) {
        if (!isEnabled(context, signalStrength)) {
            return
        }

        val telephonyManager =
            context.applicationContext.getSystemService<TelephonyManager>()!!

        val signalQuality = when (telephonyManager.signalStrength?.level) {
            CellSignalStrength.SIGNAL_STRENGTH_POOR -> "poor"
            CellSignalStrength.SIGNAL_STRENGTH_GOOD -> "good"
            CellSignalStrength.SIGNAL_STRENGTH_MODERATE -> "moderate"
            CellSignalStrength.SIGNAL_STRENGTH_GREAT -> "great"
            else -> STATE_UNKNOWN
        }
        onSensorUpdated(
            context,
            signalStrength,
            telephonyManager.signalStrength?.cellSignalStrengths?.firstOrNull()?.dbm ?: STATE_UNKNOWN,
            signalStrength.statelessIcon,
            mapOf(
                "asu" to telephonyManager.signalStrength?.cellSignalStrengths?.firstOrNull()?.asuLevel,
                "quality" to signalQuality
            )
        )
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun updateDataNetworkType(context: Context) {
        if (!isEnabled(context, dataNetworkType)) {
            return
        }
        val telephonyManager =
            context.applicationContext.getSystemService<TelephonyManager>()!!

        onSensorUpdated(
            context,
            dataNetworkType,
            dataTypeMap.getOrDefault(telephonyManager.dataNetworkType, STATE_UNKNOWN),
            dataNetworkType.statelessIcon,
            mapOf("options" to dataTypeMap.values.toList())
        )
    }
}
