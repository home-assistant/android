package io.homeassistant.companion.android.bluetooth

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.lang.reflect.Method

object BluetoothUtils {
    fun getBluetoothDevices(context: Context): List<BluetoothDevice> {
        val devices: MutableList<BluetoothDevice> = ArrayList()

        val bluetoothManager =
            (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)

        if (bluetoothManager.adapter != null) {

            val adapter = bluetoothManager.adapter
            val isBtOn = adapter.isEnabled

            if (isBtOn) {
                val bondedDevices = adapter.bondedDevices
                for (btDev in bondedDevices) {
                    devices.add(BluetoothDevice(btDev.address, btDev.name, true, isConnected(btDev)))
                }
                val btConnectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
                for (btDev in btConnectedDevices) {
                    devices.add(BluetoothDevice(btDev.address, btDev.name, false, isConnected(btDev)))
                }
            }
        }
        return devices
    }
    fun isOn(context: Context): Boolean {
        val bluetoothManager =
            (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)

        if (bluetoothManager.adapter != null) {
            val adapter = bluetoothManager.adapter
            return adapter.isEnabled
        }
        return false
    }
    private fun isConnected(device: android.bluetooth.BluetoothDevice): Boolean {
        return try {
            val m: Method = device.javaClass.getMethod("isConnected")
            m.invoke(device) as Boolean
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }
}
