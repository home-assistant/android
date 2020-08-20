package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.homeassistant.companion.android.domain.integration.SensorRegistration

class PhoneStateSensorManager : SensorManager {

    companion object {
        private const val TAG = "PhoneStateSM"
        val phoneState = SensorManager.BasicSensor(
            "phone_state",
            "sensor",
            "Phone State"
        )

        val sim_1 = SensorManager.BasicSensor(
            "sim_1",
            "sensor",
            "SIM_1"
        )

        val sim_2 = SensorManager.BasicSensor(
            "sim_2",
            "sensor",
            "SIM_2"
        )
    }

    override val name: String
        get() = "Phone Sensors"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            listOf(phoneState, sim_1, sim_2)
        else listOf(phoneState)

    override fun requiredPermissions(): Array<String> {
        return arrayOf(Manifest.permission.READ_PHONE_STATE)
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            phoneState.id -> getPhoneStateSensor(context)
            sim_1.id -> getSimSensor(context, 0)
            sim_2.id -> getSimSensor(context, 1)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getPhoneStateSensor(context: Context): SensorRegistration<Any> {
        var currentPhoneState = "unavailable"
        if (checkPermission(context)) {
            val telephonyManager =
                (context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)

            currentPhoneState = when (telephonyManager.callState) {
                0 -> "idle"
                1 -> "ringing"
                2 -> "offhook"
                else -> "unknown"
            }
        }

        var phoneIcon = "mdi:phone"
        if (currentPhoneState == "ringing" || currentPhoneState == "offhook")
            phoneIcon += "-in-talk"

        return phoneState.toSensorRegistration(
            currentPhoneState,
            phoneIcon,
            mapOf()
        )
    }

    private fun getSimSensor(context: Context, slotIndex: Int): SensorRegistration<Any> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            var displayName = "Unavailable"
            val attrs = mutableMapOf<String, Any>()

            if (checkPermission(context)) {
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

            if (slotIndex == 1) {
                return sim_2.toSensorRegistration(
                    displayName,
                    "mdi:sim",
                    attrs
                )
            }
            return sim_1.toSensorRegistration(
                displayName,
                "mdi:sim",
                attrs
            )
        } else throw IllegalArgumentException("Sensor is not supported in current OS version. API 22 or above is required")
    }
}
