package io.homeassistant.companion.android.sensors

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import io.homeassistant.companion.android.domain.integration.Sensor
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import io.homeassistant.companion.android.util.PermissionManager

class PhoneStateSensorManager : SensorManager {

    companion object {
        private const val TAG = "PhoneStateSM"
    }

    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        val sensorRegistrations = mutableListOf<SensorRegistration<Any>>()

        getPhoneStateSensor(context)?.let {
            sensorRegistrations.add(
                SensorRegistration(
                    it,
                    "Phone State"
                )
            )
        }

        return sensorRegistrations
    }

    override fun getSensors(context: Context): List<Sensor<Any>> {
        val sensors = mutableListOf<Sensor<Any>>()

        getPhoneStateSensor(context)?.let {
            sensors.add(it)
        }

        return sensors
    }

    private fun getPhoneStateSensor(context: Context): Sensor<Any>? {

        if (!PermissionManager.checkPhoneStatePermission(context)) {
            Log.w(TAG, "Tried getting phone state without permission.")
            return null
        }

        val telephonyManager = (context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)

        val phoneState: String = when (telephonyManager.callState) {
            0 -> "idle"
            1 -> "ringing"
            2 -> "offhook"
            else -> "unknown"
        }

        var phoneIcon = "mdi:phone"
        if (phoneState == "ringing" || phoneState == "offhook")
            phoneIcon += "-in-talk"

        return Sensor(
            "phone_state",
            phoneState,
            "sensor",
            phoneIcon,
            mapOf()
        )
    }
}
