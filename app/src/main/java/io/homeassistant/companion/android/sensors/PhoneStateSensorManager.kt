package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

class PhoneStateSensorManager : SensorManager {

    companion object {
        private const val TAG = "PhoneStateSM"
        val phoneState = SensorManager.BasicSensor(
            "phone_state",
            "sensor",
            commonR.string.basic_sensor_name_phone,
            commonR.string.sensor_description_phone_state,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#phone-state-sensor"
        )

        val sim_1 = SensorManager.BasicSensor(
            "sim_1",
            "sensor",
            commonR.string.basic_sensor_name_sim1,
            commonR.string.sensor_description_sim_1,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val sim_2 = SensorManager.BasicSensor(
            "sim_2",
            "sensor",
            commonR.string.basic_sensor_name_sim2,
            commonR.string.sensor_description_sim_2,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#cellular-provider-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_phone

    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            listOf(phoneState, sim_1, sim_2)
        else listOf(phoneState)
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

    private fun checkPhoneState(context: Context) {
        if (isEnabled(context, phoneState.id)) {
            var currentPhoneState = "unknown"

            if (checkPermission(context, phoneState.id)) {
                val telephonyManager =
                    (context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)

                currentPhoneState = when (telephonyManager.callState) {
                    TelephonyManager.CALL_STATE_IDLE -> "idle"
                    TelephonyManager.CALL_STATE_RINGING -> "ringing"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
                    else -> "unknown"
                }
            }

            updatePhoneStateSensor(context, currentPhoneState)
        }
    }

    private fun updatePhoneStateSensor(context: Context, state: String) {
        var phoneIcon = "mdi:phone"
        if (state == "ringing" || state == "offhook")
            phoneIcon += "-in-talk"

        onSensorUpdated(
            context,
            phoneState,
            state,
            phoneIcon,
            mapOf()
        )
    }

    private fun updateSimSensor(context: Context, slotIndex: Int) {
        val basicSimSensor = when (slotIndex) {
            0 -> sim_1
            1 -> sim_2
            else -> throw IllegalArgumentException("Invalid sim slot: $slotIndex")
        }
        if (!isEnabled(context, basicSimSensor.id))
            return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            var displayName = "Unavailable"
            val attrs = mutableMapOf<String, Any>()

            if (checkPermission(context, basicSimSensor.id)) {
                val subscriptionManager =
                    (context.applicationContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)) as SubscriptionManager
                val info: SubscriptionInfo? = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)

                if (info != null) {
                    displayName = info.displayName.toString()
                    attrs["carrier name"] = info.carrierName
                    attrs["iso country code"] = info.countryIso
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        attrs["carrier id"] = info.carrierId
                        attrs["mcc"] = info.mccString.toString()
                        attrs["mnc"] = info.mncString.toString()
                        attrs["is opportunistic"] = info.isOpportunistic
                        if (info.dataRoaming == SubscriptionManager.DATA_ROAMING_ENABLE) attrs["data roaming"] = "enable"
                        else attrs["data roaming"] = "disable"
                    }
                }
            }

            onSensorUpdated(
                context,
                basicSimSensor,
                displayName,
                "mdi:sim",
                attrs
            )
        }
    }
}
