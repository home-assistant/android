package io.homeassistant.companion.android.sensors

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile.GATT
import android.content.Context
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import java.lang.reflect.Method

class BluetoothSensorManager : SensorManager {
    companion object {
        private const val TAG = "BluetoothSM"
        private val bluetoothConnection = SensorManager.BasicSensor(
            "bluetooth_connection",
            "sensor",
            "Bluetooth Connection",
            unitOfMeasurement = "connection(s)"
        )
    }

    override val name: String
        get() = "Bluetooth Sensors"
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(bluetoothConnection)

    override fun requiredPermissions(): Array<String> {
        return arrayOf(Manifest.permission.BLUETOOTH)
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            bluetoothConnection.id -> getBluetoothConnectionSensor(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getBluetoothConnectionSensor(context: Context): SensorRegistration<Any> {

        var connectedNotPairedAddress = ""
        var totalConnectedDevices = 0
        val icon = "mdi:bluetooth"
        var connectedPairedDevices: MutableList<String> = ArrayList()
        var connectedNotPairedDevices: MutableList<String> = ArrayList()
        var bondedString = ""
        var isBtOn = false

        if (checkPermission(context)) {

            val bluetoothManager =
                (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)

            val btConnectedDevices = bluetoothManager.getConnectedDevices(GATT)
            var connectedAddress = ""

            var adapter = bluetoothManager.adapter
            isBtOn = adapter.isEnabled

            if (isBtOn) {
                var bondedDevices = adapter.bondedDevices
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
        return bluetoothConnection.toSensorRegistration(
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
