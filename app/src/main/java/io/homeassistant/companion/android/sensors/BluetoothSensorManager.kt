package io.homeassistant.companion.android.sensors

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile.GATT
import android.content.Context
import io.homeassistant.companion.android.R
import java.lang.reflect.Method

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
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_bluetooth
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(bluetoothConnection)

    override fun requiredPermissions(): Array<String> {
        return arrayOf(Manifest.permission.BLUETOOTH)
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateBluetoothConnectionSensor(context)
    }

    private fun updateBluetoothConnectionSensor(context: Context) {
        if (!isEnabled(context, bluetoothConnection.id))
            return

        var connectedNotPairedAddress = ""
        var totalConnectedDevices = 0
        val icon = "mdi:bluetooth"
        val connectedPairedDevices: MutableList<String> = ArrayList()
        val connectedNotPairedDevices: MutableList<String> = ArrayList()
        var bondedString = ""
        var isBtOn = false

        if (checkPermission(context)) {

            val bluetoothManager =
                (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)

            if (bluetoothManager.adapter != null) {
                val btConnectedDevices = bluetoothManager.getConnectedDevices(GATT)
                var connectedAddress = ""

                val adapter = bluetoothManager.adapter
                isBtOn = adapter.isEnabled

                if (isBtOn) {
                    val bondedDevices = adapter.bondedDevices
                    bondedString = bondedDevices.toString()
                    for (BluetoothDevice in bondedDevices) {
                        if (isConnected(BluetoothDevice)) {
                            connectedAddress = BluetoothDevice.address
                            connectedPairedDevices.add(connectedAddress)
                            totalConnectedDevices += 1
                        }
                    }
                    for (BluetoothDevice in btConnectedDevices) {
                        if (isConnected(BluetoothDevice)) {
                            connectedNotPairedAddress = BluetoothDevice.address
                            connectedNotPairedDevices.add(connectedNotPairedAddress)
                            if (connectedNotPairedAddress != connectedAddress) {
                                totalConnectedDevices += 1
                            }
                        }
                    }
                }
            }
        }
        onSensorUpdated(
            context,
            bluetoothConnection,
            totalConnectedDevices,
            icon,
            mapOf(
                "connected_paired_devices" to connectedPairedDevices,
                "connected_not_paired_devices" to connectedNotPairedDevices,
                "is_bt_on" to isBtOn,
                "paired_devices" to bondedString
            )
        )
    }

    private fun isConnected(device: BluetoothDevice): Boolean {
        return try {
            val m: Method = device.javaClass.getMethod("isConnected")
            m.invoke(device) as Boolean
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }
}
