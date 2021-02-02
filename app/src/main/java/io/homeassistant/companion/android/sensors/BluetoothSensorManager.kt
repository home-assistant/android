package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.bluetooth.BluetoothUtils
import io.homeassistant.companion.android.bluetooth.ble.BLETransmitter
import io.homeassistant.companion.android.bluetooth.ble.TransmitterManager

class BluetoothSensorManager : SensorManager {
    companion object {
        private const val BLE_ID1 = "UUID"
        private const val BLE_ID2 = "Major"
        private const val BLE_ID3 = "Minor"
        private const val DEFAULT_BLE_ID1 = "2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6"
        private const val DEFAULT_BLE_ID2 = "100"
        private const val DEFAULT_BLE_ID3 = "1"
        private const val TAG = "BluetoothSM"
        private var bleTransmitterDevice = BLETransmitter("", "", "", false, "")
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
                "binary_sensor",
                R.string.basic_sensor_name_bluetooth_ble_emitter,
                R.string.sensor_description_bluetooth_ble_emitter
        )
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
        updateBLEtrasnmitter(context)
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

    private fun updatedBLEDevice(context: Context): Boolean {
        var result = false
        var id1 = getSetting(context, bleTransmitter, BLE_ID1, "string", DEFAULT_BLE_ID1)
        var id2 = getSetting(context, bleTransmitter, BLE_ID2, "string", DEFAULT_BLE_ID2)
        var id3 = getSetting(context, bleTransmitter, BLE_ID3, "string", DEFAULT_BLE_ID3)
        if (bleTransmitterDevice.id1 != id1 || bleTransmitterDevice.id2 != id2 || bleTransmitterDevice.id3 != id3) {
            result = true
            bleTransmitterDevice.id1 = id1
            bleTransmitterDevice.id2 = id2
            bleTransmitterDevice.id3 = id3
        }
        return result
    }

    private fun updateBLEtrasnmitter(context: Context) {
        if (!isEnabled(context, bleTransmitter.id) && bleTransmitterDevice.transmitting) // sensor has been turned off, stop transmitting
            TransmitterManager.stopTransmitting(bleTransmitterDevice)
        else if ((isEnabled(context, bleTransmitter.id) && !bleTransmitterDevice.transmitting) || updatedBLEDevice(context)) // sensor is on, start transmitting (which only start is not already running, or details have changed
            TransmitterManager.startTransmitting(context, bleTransmitterDevice)
        val icon = if (bleTransmitterDevice.transmitting) "mdi:bluetooth" else "mdi:bluetooth-off"
        onSensorUpdated(
                context,
                bleTransmitter,
                bleTransmitterDevice.state,
                icon,
                mapOf(
                        "Id" to bleTransmitterDevice.id1 + "-" + bleTransmitterDevice.id2 + "-" + bleTransmitterDevice.id3
                )
        )
    }
}
