package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.nfc.NfcAdapter
import io.homeassistant.companion.android.common.R as commonR

class NfcSensorManager : SensorManager {
    companion object {
        private const val TAG = "NfcSensor"

        val nfcStateSensor = SensorManager.BasicSensor(
            "nfc_state",
            "binary_sensor",
            commonR.string.sensor_name_nfc_sensor,
            commonR.string.sensor_description_nfc_sensor,
            "mdi:nfc",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT
        )
    }

    override fun docsLink() = "https://companion.home-assistant.io/docs/core/sensors#nfc-sensor"
    override val name = commonR.string.sensor_name_nfc_sensor

    override suspend fun getAvailableSensors(context: Context) = listOf(nfcStateSensor)

    override fun requiredPermissions(sensorId: String) = emptyArray<String>()

    override fun requestSensorUpdate(context: Context) = updateNfcState(context)

    override fun hasSensor(context: Context): Boolean {
        return NfcAdapter.getDefaultAdapter(context) != null
    }

    private fun updateNfcState(context: Context) {
        if (!isEnabled(context, nfcStateSensor)) {
            return
        }

        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        val nfcEnabled = nfcAdapter?.isEnabled == true

        onSensorUpdated(
            context,
            nfcStateSensor,
            nfcEnabled,
            nfcStateSensor.statelessIcon,
            emptyMap()
        )
    }
}
