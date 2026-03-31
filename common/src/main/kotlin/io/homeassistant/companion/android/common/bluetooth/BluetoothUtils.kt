package io.homeassistant.companion.android.common.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.core.content.getSystemService
import java.lang.reflect.Method

object BluetoothUtils {
    @SuppressLint("MissingPermission")
    fun getBluetoothDevices(context: Context): List<BluetoothDevice> {
        val devices: MutableList<BluetoothDevice> = ArrayList()

        val bluetoothManager =
            context.applicationContext.getSystemService<BluetoothManager>()!!

        if (bluetoothManager.adapter != null) {
            val adapter = bluetoothManager.adapter
            val isBtOn = adapter.isEnabled

            if (isBtOn) {
                val bondedDevices = adapter.bondedDevices
                for (btDev in bondedDevices) {
                    val name = btDev.name ?: btDev.address
                    devices.add(
                        BluetoothDevice(
                            btDev.address,
                            name,
                            btDev.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED,
                            isConnected(btDev),
                        ),
                    )
                }
                val btConnectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
                for (btDev in btConnectedDevices) {
                    if (devices.any { it.address == btDev.address }) continue
                    val name = btDev.name ?: btDev.address
                    devices.add(
                        BluetoothDevice(
                            btDev.address,
                            name,
                            btDev.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED,
                            isConnected(btDev),
                        ),
                    )
                }
            }
        }
        return devices
    }
    fun isOn(context: Context): Boolean {
        val bluetoothManager =
            context.applicationContext.getSystemService<BluetoothManager>()!!

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
    fun supportsTransmitter(context: Context): Boolean {
        val bluetoothManager =
            context.applicationContext.getSystemService<BluetoothManager>()!!
        val adapter = bluetoothManager.adapter

        return adapter?.isMultipleAdvertisementSupported ?: false
    }
}
