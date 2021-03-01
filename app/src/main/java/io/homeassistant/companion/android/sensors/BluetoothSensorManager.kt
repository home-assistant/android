package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.bluetooth.ble.IBeaconTransmitter
import io.homeassistant.companion.android.bluetooth.ble.TransmitterManager
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.sensor.Sensor
import java.util.UUID
import kotlin.collections.ArrayList

class BluetoothSensorManager : SensorManager {
    companion object {

        private const val BLE_ID1 = "UUID"
        private const val BLE_ID2 = "Major"
        private const val BLE_ID3 = "Minor"
        private const val BLE_TRANSMIT_POWER = "transmit_power"
        private const val ENABLE_TOGGLE_ALL = "Include when enabling all sensors"
        private const val DEFAULT_BLE_TRANSMIT_POWER = "ultraLow"
        private const val DEFAULT_BLE_ID2 = "100"
        private const val DEFAULT_BLE_ID3 = "1"
        private var priorBluetoothStateEnabled = false

        // private const val TAG = "BluetoothSM"
        private var bleTransmitterDevice = IBeaconTransmitter("", "", "", transmitPower = "", transmitting = false, state = "", restartRequired = false)
        val bluetoothConnection = SensorManager.BasicSensor(
                "bluetooth_connection",
                "sensor",
                R.string.basic_sensor_name_bluetooth,
                R.string.sensor_description_bluetooth_connection,
                unitOfMeasurement = "connection(s)"
        )
        val bluetoothState = SensorManager.BasicSensor(
                "bluetooth_state",
                "binary_sensor",
                R.string.basic_sensor_name_bluetooth_state,
                R.string.sensor_description_bluetooth_state
        )
        val bleTransmitter = SensorManager.BasicSensor(
                "ble_emitter",
                "sensor",
                R.string.basic_sensor_name_bluetooth_ble_emitter,
                R.string.sensor_description_bluetooth_ble_emitter
        )

        fun enableDisableBLETransmitter(context: Context, enabled: Boolean) {
            val sensorDao = AppDatabase.getInstance(context).sensorDao()
            var sensorEntity = sensorDao.get(bleTransmitter.id)
            if (sensorEntity != null) {
                sensorEntity.enabled = enabled
                sensorDao.update(sensorEntity)
            } else {
                sensorEntity = Sensor(bleTransmitter.id, enabled, false, "")
                sensorDao.add(sensorEntity)
            }
            TransmitterManager.stopTransmitting(bleTransmitterDevice) // stop in all instances, clean up state if start required
            if (enabled) {
                TransmitterManager.startTransmitting(context, bleTransmitterDevice)
            }
        }
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_bluetooth
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(bluetoothConnection, bluetoothState, bleTransmitter)

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.BLUETOOTH)
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateBluetoothConnectionSensor(context)
        updateBluetoothState(context)
        updateBLETransmitter(context)
    }

    private fun updateBluetoothConnectionSensor(context: Context) {
        if (!isEnabled(context, bluetoothConnection.id))
            return

        var totalConnectedDevices = 0
        val icon = "mdi:bluetooth"
        var connectedPairedDevices: List<String> = ArrayList()
        var connectedNotPairedDevices: List<String> = ArrayList()
        var bondedString = ""

        if (checkPermission(context, bluetoothConnection.id)) {

            val bluetoothDevices = BluetoothUtils.getBluetoothDevices(context)
            bondedString = bluetoothDevices.filter { b -> b.paired }.map { it.address }.toString()
            connectedPairedDevices = bluetoothDevices.filter { b -> b.paired && b.connected }.map { it.address }
            connectedNotPairedDevices = bluetoothDevices.filter { b -> !b.paired && b.connected }.map { it.address }
            totalConnectedDevices = bluetoothDevices.filter { b -> b.connected }.count()
        }
        onSensorUpdated(
                context,
                bluetoothConnection,
                totalConnectedDevices,
                icon,
                mapOf(
                        "connected_paired_devices" to connectedPairedDevices,
                        "connected_not_paired_devices" to connectedNotPairedDevices,
                        "paired_devices" to bondedString
                )
        )
    }

    private fun isBtOn(context: Context): Boolean {
        var btOn = false
        if (checkPermission(context, bluetoothState.id)) {
            btOn = BluetoothUtils.isOn(context)
        }
        return btOn
    }

    private fun updateBluetoothState(context: Context) {
        if (!isEnabled(context, bluetoothState.id))
            return
        val icon = if (isBtOn(context)) "mdi:bluetooth" else "mdi:bluetooth-off"
        onSensorUpdated(
                context,
                bluetoothState,
                isBtOn(context),
                icon,
                mapOf()
        )
    }

    override fun enableToggleAll(context: Context, sensorId: String): Boolean {
        if (sensorId == bleTransmitter.id) {
            return getSetting(context, bleTransmitter, ENABLE_TOGGLE_ALL, "toggle", "false").toBoolean()
        }
        return super.enableToggleAll(context, sensorId)
    }

    private fun updateBLEDevice(context: Context) {
        addSettingIfNotPresent(context, bleTransmitter, ENABLE_TOGGLE_ALL, "toggle", "false")

        var id1 = getSetting(context, bleTransmitter, BLE_ID1, "string", UUID.randomUUID().toString())
        var id2 = getSetting(context, bleTransmitter, BLE_ID2, "string", DEFAULT_BLE_ID2)
        var id3 = getSetting(context, bleTransmitter, BLE_ID3, "string", DEFAULT_BLE_ID3)
        var transmitPower = getSetting(context, bleTransmitter, BLE_TRANSMIT_POWER, "list", DEFAULT_BLE_TRANSMIT_POWER)
        bleTransmitterDevice.restartRequired = false
        if (bleTransmitterDevice.uuid != id1 || bleTransmitterDevice.major != id2 ||
                bleTransmitterDevice.minor != id3 || bleTransmitterDevice.transmitPower != transmitPower ||
                isBtOn(context) != priorBluetoothStateEnabled) {
            bleTransmitterDevice.restartRequired = true
        }
        // stash the current BT state to help us know if we need to restart if BT state turns from off to on
        priorBluetoothStateEnabled = isBtOn(context)

        bleTransmitterDevice.uuid = id1
        bleTransmitterDevice.major = id2
        bleTransmitterDevice.minor = id3
        bleTransmitterDevice.transmitPower = transmitPower
    }

    private fun updateBLETransmitter(context: Context) {
        // sensor disabled, stop transmitting if we have been
        if (!isEnabled(context, bleTransmitter.id)) {
                TransmitterManager.stopTransmitting(bleTransmitterDevice)
            return
        }
        // transmit when BT is on, if we are not already transmitting, or details have changed
        updateBLEDevice(context)
        if (isBtOn(context)) {
            if (!bleTransmitterDevice.transmitting || bleTransmitterDevice.restartRequired) {
                TransmitterManager.startTransmitting(context, bleTransmitterDevice)
            }
        } else {
            // BT off, stop transmitting if we have been
            TransmitterManager.stopTransmitting(bleTransmitterDevice)
        }

        val state = if (isBtOn(context)) bleTransmitterDevice.state else "Bluetooth is turned off"
        val icon = if (bleTransmitterDevice.transmitting) "mdi:bluetooth" else "mdi:bluetooth-off"
        onSensorUpdated(
                context,
                bleTransmitter,
                state,
                icon,
                mapOf(
                        "id" to bleTransmitterDevice.uuid + "-" + bleTransmitterDevice.major + "-" + bleTransmitterDevice.minor,
                        "Transmitting power" to bleTransmitterDevice.transmitPower
                )
        )
    }
}
