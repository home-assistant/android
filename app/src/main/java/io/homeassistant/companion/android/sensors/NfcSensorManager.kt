package io.homeassistant.companion.android.sensors

import android.content.Context
import android.nfc.NfcAdapter
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.R as commonR

// TODO move to common
class NfcSensorManager : SensorManager {
    companion object {
        private const val TAG = "NfcSensor"

        val nfcSensor = SensorManager.BasicSensor(
            "nfc_state",
            "binary_sensor",
            commonR.string.sensor_name_nfc_sensor,
            commonR.string.sensor_description_nfc_sensor,
            "mdi:nfc",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#nfc-sensor" // TODO
    }
    override val name: Int
        get() = commonR.string.sensor_name_nfc_sensor

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(nfcSensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray() // TODO ?
    }

    override fun requestSensorUpdate(context: Context) {
        updateNfcState(context)
    }

    override fun hasSensor(context: Context): Boolean {
        return NfcAdapter.getDefaultAdapter(context) != null
    }

    private fun updateNfcState(context: Context) {
        if (!isEnabled(context, nfcSensor)) {
            return
        }

        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        val nfcEnabled = nfcAdapter?.isEnabled == true

        onSensorUpdated(
            context,
            nfcSensor,
            nfcEnabled,
            nfcSensor.statelessIcon,
            emptyMap() // TODO
        )
    }
}
