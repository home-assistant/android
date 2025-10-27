package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.nfc.NfcAdapter
import io.homeassistant.companion.android.common.R as commonR

class NfcSensorManager : SensorManager {
    companion object {
        val nfcStateSensor = SensorManager.BasicSensor(
            "nfc_state",
            "binary_sensor",
            commonR.string.basic_sensor_name_nfc_state,
            commonR.string.sensor_description_nfc_state,
            "mdi:nfc-variant",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    override fun docsLink() = "https://companion.home-assistant.io/docs/core/sensors#nfc-state-sensor"
    override val name = commonR.string.sensor_name_nfc

    override suspend fun getAvailableSensors(context: Context) = listOf(nfcStateSensor)

    override fun requiredPermissions(context: Context, sensorId: String) = emptyArray<String>()

    override suspend fun requestSensorUpdate(context: Context) = updateNfcState(context)

    override fun hasSensor(context: Context): Boolean {
        return NfcAdapter.getDefaultAdapter(context) != null
    }

    private suspend fun updateNfcState(context: Context) {
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
            emptyMap(),
        )
    }
}
