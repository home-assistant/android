package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
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
    }

    override val name: String
        get() = "Phone Sensors"

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(phoneState)

    override fun requiredPermissions(): Array<String> {
        return arrayOf(Manifest.permission.READ_PHONE_STATE)
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            phoneState.id -> getPhoneStateSensor(context)
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
}
