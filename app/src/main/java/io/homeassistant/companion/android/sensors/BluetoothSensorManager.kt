package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.bluetooth.BluetoothUtils

class BluetoothSensorManager : SensorManager {
    companion object {
        private const val TAG = "BluetoothSM"
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
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_bluetooth
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(bluetoothConnection, bluetoothState)

    override fun requiredPermissions(sensorId: String): Array<String> {
        return arrayOf(Manifest.permission.BLUETOOTH)
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateBluetoothConnectionSensor(context)
        updateBluetoothState(context)
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

    private fun updateBluetoothState(context: Context) {
        if (!isEnabled(context, bluetoothState.id))
            return

        var isBtOn = false

        if (checkPermission(context, bluetoothState.id)) {
            isBtOn = BluetoothUtils.isOn(context)
        }
        val icon = if (isBtOn) "mdi:bluetooth" else "mdi:bluetooth-off"

        onSensorUpdated(
            context,
            bluetoothState,
            isBtOn,
            icon,
            mapOf()
        )
    }
}
