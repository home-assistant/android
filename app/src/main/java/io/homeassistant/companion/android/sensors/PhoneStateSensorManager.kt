package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.homeassistant.companion.android.R

class PhoneStateSensorManager : SensorManager {

    companion object {
        private const val TAG = "PhoneStateSM"
        val phoneState = SensorManager.BasicSensor(
            "phone_state",
            "sensor",
            "Phone State",
            R.string.sensor_description_phone_state
        )

        val sim_1 = SensorManager.BasicSensor(
            "sim_1",
            "sensor",
            "SIM_1",
            R.string.sensor_description_sim_1
        )

        val sim_2 = SensorManager.BasicSensor(
            "sim_2",
            "sensor",
            "SIM_2",
            R.string.sensor_description_sim_2
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

    override fun requestSensorUpdate(
        context: Context
    ) {
        updatePhoneStateSensor(context)
        updateSimSensor(context, 0)
        updateSimSensor(context, 1)
    }

    private fun updatePhoneStateSensor(context: Context) {
        if (!isEnabled(context, phoneState.id))
            return
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

        onSensorUpdated(context,
            phoneState,
            currentPhoneState,
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

            onSensorUpdated(context,
                basicSimSensor,
                displayName,
                "mdi:sim",
                attrs
            )
        }
    }
}
