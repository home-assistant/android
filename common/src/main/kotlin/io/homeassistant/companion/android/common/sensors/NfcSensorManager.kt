package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.nfc.NfcAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NfcSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        @ProvidesSensor
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

    override suspend fun getAvailableSensors() = listOf(nfcStateSensor)

    override fun requiredPermissions(sensorId: String) = emptyArray<String>()

    override suspend fun requestSensorUpdate() = updateNfcState()

    override fun hasSensor(): Boolean {
        return NfcAdapter.getDefaultAdapter(applicationContext) != null
    }

    private suspend fun updateNfcState() {
        if (!isEnabled(nfcStateSensor)) {
            return
        }

        val nfcAdapter = NfcAdapter.getDefaultAdapter(applicationContext)
        val nfcEnabled = nfcAdapter?.isEnabled == true

        onSensorUpdated(
            nfcStateSensor,
            nfcEnabled,
            nfcStateSensor.statelessIcon,
            emptyMap(),
        )
    }
}
