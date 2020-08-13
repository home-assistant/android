package io.homeassistant.companion.android.sensors

import android.content.Context
import android.telephony.TelephonyManager
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import io.homeassistant.companion.android.util.PermissionManager

class PhoneStateSensorManager : SensorManager {

    companion object {
        const val ID_PHONE = "phone_state"
        private const val TAG = "PhoneStateSM"
    }

    override val name: String
        get() = "Phone Sensors"

    override fun requiredPermissions(): Array<String> {
        return PermissionManager.getPhonePermissionArray()
    }

    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        return listOf(getPhoneStateSensor(context))
    }

    private fun getPhoneStateSensor(context: Context): SensorRegistration<Any> {
        var phoneState = "unavailable"
        if (PermissionManager.checkPhoneStatePermission(context)) {
            val telephonyManager =
                (context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)

            phoneState = when (telephonyManager.callState) {
                0 -> "idle"
                1 -> "ringing"
                2 -> "offhook"
                else -> "unknown"
            }
        }

        var phoneIcon = "mdi:phone"
        if (phoneState == "ringing" || phoneState == "offhook")
            phoneIcon += "-in-talk"

        return SensorRegistration(
            ID_PHONE,
            phoneState,
            "sensor",
            phoneIcon,
            mapOf(),
            "Phone State"
        )
    }
}
