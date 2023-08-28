package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.getSystemService
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.R as commonR

class PhoneStateSensorManager : SensorManager {

    companion object {
        private const val TAG = "PhoneStateSM"
        val phoneState = SensorManager.BasicSensor(
            "phone_state",
            "sensor",
            commonR.string.basic_sensor_name_phone,
            commonR.string.sensor_description_phone_state,
            "mdi:phone",
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
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#cellular-provider-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_phone
    override fun hasSensor(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }
    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            listOf(phoneState, sim_1, sim_2)
        } else {
            listOf(phoneState)
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
            mapOf()
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
}
